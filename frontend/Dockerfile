FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=${BACKEND_URL}
ENV PORT=80
ENV NGINX_ENVSUBST_VARS='$BACKEND_URL $PORT'
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist/poker-night/browser /usr/share/nginx/html
EXPOSE 80
