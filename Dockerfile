FROM eclipse-temurin:17-jdk
RUN apt-get update && apt-get install -y curl unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
ENV KOTLIN_LSP_VERSION=0.253.10629
RUN curl -L "https://download-cdn.jetbrains.com/kotlin-lsp/${KOTLIN_LSP_VERSION}/kotlin-${KOTLIN_LSP_VERSION}.zip" \
    -o kotlin-lsp.zip \
    && unzip kotlin-lsp.zip \
    && rm kotlin-lsp.zip

RUN chmod +x kotlin-lsp.sh
EXPOSE 9999
CMD ["sh", "-c", "./kotlin-lsp.sh", "--multi-client", "--scoped"]

