-- 七个业务库一次性建库：MySQL 容器首次初始化时经 /docker-entrypoint-initdb.d 自动执行。
-- 库名遵循 AGENTS.md 11.3：lp_{域}。业务账号口令为本地开发占位值，生产凭据仅经 K8s Secret 下发。
-- 业务表不在此创建：统一由 Flyway 迁移管理（db/migration/{服务}/V1__baseline.sql 起步）。

CREATE DATABASE IF NOT EXISTS lp_ums  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lp_cms  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lp_oms  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lp_lms  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lp_ugc  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lp_ai   DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS lp_ops  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'lp_app'@'%' IDENTIFIED BY 'lp_app_dev';

GRANT ALL PRIVILEGES ON lp_ums.* TO 'lp_app'@'%';
GRANT ALL PRIVILEGES ON lp_cms.* TO 'lp_app'@'%';
GRANT ALL PRIVILEGES ON lp_oms.* TO 'lp_app'@'%';
GRANT ALL PRIVILEGES ON lp_lms.* TO 'lp_app'@'%';
GRANT ALL PRIVILEGES ON lp_ugc.* TO 'lp_app'@'%';
GRANT ALL PRIVILEGES ON lp_ai.*  TO 'lp_app'@'%';
GRANT ALL PRIVILEGES ON lp_ops.* TO 'lp_app'@'%';

FLUSH PRIVILEGES;
