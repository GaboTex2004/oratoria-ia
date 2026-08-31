# Oratoria IA

Plataforma SaaS modular para entrenamiento de oratoria.

- `frontend`: React, TypeScript y Vite (`http://localhost:5173`).
- `backend`: Spring Boot y Java 21 (`http://localhost:8081`).
- `ai-service`: esqueleto FastAPI para trabajo futuro de IA.
- PostgreSQL 17 en Docker (`localhost:5434`, base `oratoria_db`).

## Desarrollo local

```powershell
docker compose up -d
cd backend
mvn spring-boot:run
```

En otra terminal:

```powershell
cd frontend
npm.cmd run dev
```

Verificación: `GET http://localhost:8081/api/test`.

Consulta `docs/ARCHITECTURE.md` y `docs/TEAM_WORKFLOW.md` antes de agregar módulos.
