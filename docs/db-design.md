SHORTENER SERVICE DB (Postgres)
┌─────────────────────────────┐      ┌──────────────────────────────────┐
│ users                       │      │ links                            │
│  id            BIGINT  PK   │◄─────│  id          BIGINT  PK          │
│  email         VARCHAR UNIQUE│     │  code        VARCHAR(20) UNIQUE  │
│  password_hash VARCHAR      │      │  long_url    VARCHAR(2048)       │
│  role          VARCHAR      │      │  user_id     BIGINT FK, INDEX    │
│  created_at    TIMESTAMP    │      │  expires_at  TIMESTAMP nullable  │
└─────────────────────────────┘      │  is_active   BOOLEAN default true│
                                     │  created_at  TIMESTAMP           │
                                     └──────────────────────────────────┘

ANALYTICS SERVICE DB (Postgres)
┌──────────────────────────────────┐   ┌─────────────────────────────┐
│ click_events                     │   │ daily_click_stats           │
│  event_id   UUID  PK             │   │  code     VARCHAR(20)       │
│  code       VARCHAR(20)          │   │  day      DATE              │
│  clicked_at TIMESTAMP            │   │  clicks   BIGINT            │
│  referrer   VARCHAR(500) nullable│   │  PK (code, day)             │
│  device     VARCHAR(50)          │   └─────────────────────────────┘
│  INDEX (code, clicked_at)        │
└──────────────────────────────────┘