# ---- Build stage ----
FROM rust:1.93-slim AS builder
WORKDIR /workspace

RUN apt-get update && apt-get install -y pkg-config libssl-dev && rm -rf /var/lib/apt/lists/*

# Cache dependencies by building dummy crates first
COPY Cargo.toml Cargo.lock ./
COPY price-solver/Cargo.toml ./price-solver/
COPY api-server/Cargo.toml ./api-server/

RUN mkdir -p price-solver/src api-server/src && \
    printf 'pub fn dummy() {}' > price-solver/src/lib.rs && \
    printf 'fn main() {}' > api-server/src/main.rs && \
    cargo build --release --package api-server 2>/dev/null || true

# Build real source
COPY price-solver/ ./price-solver/
COPY api-server/ ./api-server/
RUN touch price-solver/src/lib.rs api-server/src/main.rs && \
    cargo build --release --package api-server

# ---- Runtime stage ----
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates libssl3 curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /workspace/target/release/api-server .
COPY api-server/migrations/ ./migrations/

EXPOSE 8080
ENV RUST_LOG=info
ENV BIND_ADDR=0.0.0.0:8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

CMD ["./api-server"]
