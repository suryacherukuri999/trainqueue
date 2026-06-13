# console

Vite + React 18 + TypeScript (strict). Submit form and a job table that polls
`GET /api/jobs` every 3s with status badges and a cancel button.

## Run
Needs the api running on `http://localhost:8080`.

```bash
npm install
npm run dev        # http://localhost:5173
```

## Test
```bash
npm test           # Vitest: job table render + cancel behaviour
```
