FROM openjdk:21-slim

RUN apt-get update && \
    apt-get install -y curl jq unzip bash && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN mkdir -p kotlin-lsp

COPY setup-kotlin-lsp.sh /app/setup-kotlin-lsp.sh
RUN chmod +x /app/setup-kotlin-lsp.sh

RUN /app/setup-kotlin-lsp.sh

EXPOSE 9990

CMD ["./kotlin-lsp/kotlin-lsp.sh", "--multi-client", "--socket", "127.0.0.1:9990"]
