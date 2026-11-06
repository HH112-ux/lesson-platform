# Serves the built operations console. `apps/admin/dist` is produced on the host by
# scripts/dev-up.sh (pnpm --filter @lp/admin build).
FROM nginx:1.27-alpine

COPY apps/admin/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
