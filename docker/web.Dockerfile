# Serves the built student web app. `apps/web/dist` is produced on the host by
# scripts/dev-up.sh (pnpm --filter @lp/web build).
FROM nginx:1.27-alpine

COPY apps/web/dist /usr/share/nginx/html
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
