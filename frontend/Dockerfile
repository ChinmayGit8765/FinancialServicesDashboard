# Stage 1: Build the Vue 3 + Vite app
FROM node:22-alpine AS build
WORKDIR /app

# Install dependencies (npm ci is deterministic from package-lock.json)
COPY package*.json ./
RUN npm ci

# Build production assets
COPY . .
RUN npm run build

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2: Serve with nginx (alpine — minimal image)
FROM nginx:alpine

# Copy compiled SPA to nginx html root
COPY --from=build /app/dist /usr/share/nginx/html

# Custom nginx config: SPA try_files + /api proxy_pass to backend
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
