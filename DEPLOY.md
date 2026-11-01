# Deploy a free public demo

This puts SeatLock on the internet for **₹0 / $0**, using three free services. None of them needs a credit card at the time of writing:

| Piece | Service | What you get for free |
|---|---|---|
| The app (built from this repo's `Dockerfile`) | [Render](https://render.com) | 1 web service, 512 MB RAM, 0.1 CPU. **Sleeps after 15 min without visitors**; the next visit wakes it up |
| PostgreSQL | [Neon](https://neon.com) | 0.5 GB storage, 100 compute-hours/month, suspends itself when idle |
| Redis | [Upstash](https://upstash.com) | 256 MB, 500,000 commands/month |

The demo runs with the **`demo` profile**: no Kafka (messages go straight from the outbox table to the handlers inside the app), a small connection pool, and settings for hosted Postgres and Redis. Everything else works exactly as in the full setup: seat holds, all locking strategies, idempotency, the payment saga, notifications, rate limiting and the waiting room.

> Free plans change. If a step below doesn't match what you see, the idea stays the same: create a Postgres database, create a Redis database, and give their connection details to Render.

**Time needed:** about 15 minutes of clicking, plus 5–10 minutes for the first build.

---

## Before you start

1. All pull requests you want live are **merged into `main`**. Render deploys `main`.
2. Use **one region for all three services**, so the app talks to the database quickly. This guide uses **Singapore** (closest to India). `render.yaml` already sets `region: singapore`.

---

## Step 1: Postgres on Neon

1. Go to **https://neon.com** → **Sign up** (you can use your GitHub account).
2. Create a project:
   - **Project name:** `seatlock`
   - **Region:** *AWS Asia Pacific (Singapore)*
   - Leave the rest as default → **Create project**.
3. On the project dashboard click **Connect**.
4. In the dialog, make sure **Connection pooling is OFF**. You want the *direct* connection: Flyway migrations don't work through the pooler.
5. Copy the connection string. It looks like this:
   ```
   postgresql://neondb_owner:AbC123xyz@ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   ```
6. Split it into the three values Render needs:

   | Render variable | Take this part | Example |
   |---|---|---|
   | `DB_URL` | `jdbc:postgresql://` + host + `/` + database + `?sslmode=require` | `jdbc:postgresql://ep-cool-name-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USER` | the part before `:` after `postgresql://` | `neondb_owner` |
   | `DB_PASSWORD` | the part between `:` and `@` | `AbC123xyz` |

   - If the string also contains `&channel_binding=require`, leave that part out.
   - The URL must start with `jdbc:postgresql://`. Java needs that prefix.

You don't need to create any tables; the app does that itself on first start (Flyway migrations).

---

## Step 2: Redis on Upstash

1. Go to **https://upstash.com** → **Sign up** (GitHub works).
2. **Create Database** (Redis):
   - **Name:** `seatlock`
   - **Primary region:** *Singapore* (ap-southeast-1)
   - **Plan:** Free → **Create**.
3. On the database page, find the connection details (the **Details** or **Connect** section):

   | Render variable | Upstash field | Example |
   |---|---|---|
   | `REDIS_HOST` | **Endpoint**: only the host name, no `https://`, no `:6379` | `apt-cat-12345.upstash.io` |
   | `REDIS_PASSWORD` | **Password** (sometimes shown as the *token*; click the eye icon to reveal it) | `AYxxxxxxxxxxxx` |

   The port (`6379`) and TLS (`REDIS_SSL=true`) are already set in `render.yaml`.

---

## Step 3: The app on Render

1. Go to **https://render.com** → **Get Started** → **sign up with GitHub**.
2. When Render asks for repository access, allow it for **`HemashreeVI/seatlock-`** (or all repositories).
3. In the Render dashboard: **New +** → **Blueprint**.
4. Pick the **`seatlock-`** repository → Render finds `render.yaml` and shows one service, **`seatlock`** (plan: *Free*).
5. Render now asks for the variables marked as secret. Paste the values from steps 1 and 2:
   - `DB_URL`, `DB_USER`, `DB_PASSWORD`
   - `REDIS_HOST`, `REDIS_PASSWORD`
6. Click **Apply** (or **Deploy Blueprint**).
7. Open the service → **Logs**. The first deploy:
   - builds the jar with Maven (a few minutes),
   - runs a short *training start* that makes later startups about twice as fast (Class Data Sharing),
   - starts the app.

   You're done when the log shows `Started SeatlockApplication` and the service status turns **Live**. Startup itself takes about 1–2 minutes on the free CPU.

Your public link is at the top of the service page, something like:

```
https://seatlock-xxxx.onrender.com
```

---

## Step 4: Check it

| Open | You should see |
|---|---|
| `https://seatlock-xxxx.onrender.com/` | The demo page: seat map, hold/pay buttons, the "50 buyers, 1 seat" race |
| `…/actuator/health` | `{"status":"UP", … "db":{"status":"UP"}, "redis":{"status":"UP"} …}` |
| `…/api/events` | The demo event (created automatically) |

Try the race button. It should say **1 winner · 49 got 409 Conflict**.

---

## How the free demo behaves

- **First visit after a quiet period is slow.** After 15 minutes without visitors Render stops the app. The next visitor waits about **1.5–2 minutes** while it starts; Render shows a "waking up" screen and the demo page keeps retrying. After that it's quick.
- **Don't keep it awake 24/7 with an uptime pinger.** While the app runs, its background jobs use the database continuously. Running it non-stop would use up Neon's 100 free compute-hours in about two to three weeks, and then the database pauses until the next month. Normal visiting is fine: every visit keeps it awake for about 15–20 minutes.
- **There's always something to book.** On every start, if no upcoming event has free seats left, the app creates a fresh demo event.
- **Holds expire after 10 minutes** as usual. Payments use the fake gateway: **Pay** always succeeds, **Pay with declined card** always fails, so you can watch the seats being released.
- **Not exposed publicly:** `/actuator/prometheus` and `/actuator/metrics` are switched off in the demo, and health shows UP/DOWN only, without details.

---

## Updating the demo

`autoDeploy` is on: every push or merge to `main` redeploys automatically. Render shows the build in **Events** and **Logs**.

---

## Troubleshooting

| Symptom in Render logs | Cause | Fix |
|---|---|---|
| `Connection to … refused`, `UnknownHostException` | `DB_URL` wrong | It must start with `jdbc:postgresql://` and contain host **and** database name (`…neon.tech/neondb?sslmode=require`) |
| `password authentication failed` | `DB_USER` / `DB_PASSWORD` swapped or incomplete | Copy them again from Neon's connection string |
| Flyway error mentioning `prepared statement` or `pooler` | You used Neon's *pooled* host (contains `-pooler`) | Use the direct connection (pooling OFF) |
| Health shows `redis: DOWN`, or `NOAUTH`, or Redis timeouts | Redis host or password wrong, or TLS off | `REDIS_HOST` = host only (no `https://`, no port), `REDIS_PASSWORD` = Upstash password, `REDIS_SSL` = `true` |
| `OutOfMemoryError` or the service restarts in a loop | Memory pressure on 512 MB | The image already uses small-memory JVM flags; make sure you didn't set a different `JAVA_OPTS` |
| Deploy fails while building | Usually a temporary network error downloading Maven dependencies | **Manual Deploy → Clear build cache & deploy** |

To change a variable later: service → **Environment** → edit → **Save changes**. Render redeploys automatically.
