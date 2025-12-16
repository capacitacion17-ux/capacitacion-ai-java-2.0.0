# 🚀 Guía de Deployment - Ticketero API

**Versión:** 1.0  
**Última actualización:** $(date +%Y-%m-%d)

---

## 📋 Prerequisitos

### **Software Requerido**
- Docker 24+
- Docker Compose 2.20+
- 4GB RAM mínimo
- 10GB espacio en disco

### **Variables de Entorno**
Crear archivo `.env.prod` basado en `.env.prod.example`:

```bash
cp .env.prod.example .env.prod
# Editar .env.prod con valores reales
```

---

## 🐳 Deployment con Docker Compose

### **1. Desarrollo Local**

```bash
# Levantar infraestructura (PostgreSQL + RabbitMQ)
docker-compose up -d

# Ejecutar aplicación localmente
./start-local.sh
```

**Servicios disponibles:**
- API: http://localhost:8080
- RabbitMQ UI: http://localhost:15672 (dev/dev123)
- PostgreSQL: localhost:5432

---

### **2. Producción**

```bash
# 1. Configurar variables de entorno
source .env.prod

# 2. Build y levantar todos los servicios
docker-compose -f docker-compose.prod.yml up -d --build

# 3. Verificar servicios
docker-compose -f docker-compose.prod.yml ps

# 4. Ver logs
docker-compose -f docker-compose.prod.yml logs -f api
```

**Servicios disponibles:**
- API: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin123)
- RabbitMQ UI: http://localhost:15672
- PostgreSQL: localhost:5432

---

## 🔍 Verificación de Deployment

### **Health Checks**

```bash
# Health check de la API
curl http://localhost:8080/actuator/health

# Respuesta esperada:
# {"status":"UP"}

# Health check detallado
curl -u admin:password http://localhost:8080/actuator/health
```

### **Verificar Servicios**

```bash
# Estado de contenedores
docker-compose -f docker-compose.prod.yml ps

# Logs de API
docker-compose -f docker-compose.prod.yml logs api

# Logs de PostgreSQL
docker-compose -f docker-compose.prod.yml logs postgres
```

---

## 🔄 Operaciones Comunes

### **Actualizar Aplicación**

```bash
# 1. Pull últimos cambios
git pull origin main

# 2. Rebuild y restart
docker-compose -f docker-compose.prod.yml up -d --build api

# 3. Verificar
docker-compose -f docker-compose.prod.yml logs -f api
```

### **Backup de Base de Datos**

```bash
# Backup
docker exec ticketero-postgres pg_dump -U ticketero_user ticketero > backup_$(date +%Y%m%d).sql

# Restore
docker exec -i ticketero-postgres psql -U ticketero_user ticketero < backup_20240101.sql
```

### **Restart Servicios**

```bash
# Restart API
docker-compose -f docker-compose.prod.yml restart api

# Restart todos los servicios
docker-compose -f docker-compose.prod.yml restart
```

### **Stop y Cleanup**

```bash
# Stop servicios
docker-compose -f docker-compose.prod.yml down

# Stop y eliminar volúmenes (⚠️ CUIDADO: elimina datos)
docker-compose -f docker-compose.prod.yml down -v
```

---

## 📊 Monitoring

### **Prometheus**

```bash
# Abrir Prometheus
open http://localhost:9090

# Queries útiles:
# - tickets_created_total
# - rate(tickets_created_total[5m])
# - jvm_memory_used_bytes
```

### **Grafana**

```bash
# Abrir Grafana
open http://localhost:3000

# Login: admin / admin123

# Agregar Data Source:
# - Type: Prometheus
# - URL: http://prometheus:9090
```

---

## 🔐 Seguridad

### **Cambiar Passwords**

```bash
# Editar .env.prod
nano .env.prod

# Actualizar:
# - DATABASE_PASSWORD
# - RABBITMQ_PASSWORD
# - ADMIN_PASSWORD
# - GRAFANA_PASSWORD

# Restart servicios
docker-compose -f docker-compose.prod.yml restart
```

### **Rotar Secrets**

```bash
# 1. Generar nuevos passwords
openssl rand -base64 32

# 2. Actualizar .env.prod
# 3. Restart servicios
```

---

## 🚨 Troubleshooting

### **API no inicia**

```bash
# Ver logs
docker-compose -f docker-compose.prod.yml logs api

# Verificar variables de entorno
docker-compose -f docker-compose.prod.yml exec api env | grep DATABASE

# Verificar conectividad a PostgreSQL
docker-compose -f docker-compose.prod.yml exec api nc -zv postgres 5432
```

### **PostgreSQL no conecta**

```bash
# Verificar que está corriendo
docker-compose -f docker-compose.prod.yml ps postgres

# Ver logs
docker-compose -f docker-compose.prod.yml logs postgres

# Conectar manualmente
docker-compose -f docker-compose.prod.yml exec postgres psql -U ticketero_user -d ticketero
```

### **RabbitMQ no procesa mensajes**

```bash
# Ver logs
docker-compose -f docker-compose.prod.yml logs rabbitmq

# Verificar colas en UI
open http://localhost:15672

# Restart RabbitMQ
docker-compose -f docker-compose.prod.yml restart rabbitmq
```

---

## 📈 Escalamiento

### **Escalar Workers**

```bash
# Escalar API a 3 instancias
docker-compose -f docker-compose.prod.yml up -d --scale api=3

# Verificar
docker-compose -f docker-compose.prod.yml ps
```

### **Aumentar Recursos**

Editar `docker-compose.prod.yml`:

```yaml
deploy:
  resources:
    limits:
      cpus: '2.0'      # Aumentar CPU
      memory: 2G       # Aumentar memoria
```

---

## 🔄 CI/CD

### **GitHub Actions**

El pipeline se ejecuta automáticamente en:
- Push a `main` o `develop`
- Pull requests a `main`

**Stages:**
1. **Test** - Ejecuta tests unitarios
2. **Build** - Construye imagen Docker
3. **Security Scan** - Escanea vulnerabilidades

### **Secrets Requeridos**

Configurar en GitHub → Settings → Secrets:

```
DOCKER_USERNAME=tu_usuario
DOCKER_PASSWORD=tu_password
```

---

## 📝 Checklist de Deployment

- [ ] Variables de entorno configuradas (`.env.prod`)
- [ ] Passwords cambiados de defaults
- [ ] Docker y Docker Compose instalados
- [ ] Puertos disponibles (8080, 5432, 5672, 9090, 3000)
- [ ] Backup de datos existentes (si aplica)
- [ ] Health checks funcionando
- [ ] Monitoring configurado (Prometheus + Grafana)
- [ ] Logs accesibles

---

## 🆘 Soporte

**Logs:**
```bash
docker-compose -f docker-compose.prod.yml logs -f
```

**Estado:**
```bash
docker-compose -f docker-compose.prod.yml ps
```

**Restart completo:**
```bash
docker-compose -f docker-compose.prod.yml down
docker-compose -f docker-compose.prod.yml up -d
```

---

**Última actualización:** $(date +%Y-%m-%d)
