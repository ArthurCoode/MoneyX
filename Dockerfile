# Imagem da aplicação MoneyX, em dois estágios (multi-stage):
#   1. "build":   JDK + Maven Wrapper compilam e empacotam o .jar (ferramentas de compilação ficam SÓ aqui);
#   2. "runtime": JRE mínimo + o .jar, rodando com um usuário sem privilégios.
#
# As imagens base são fixadas por digest (sha256): a tag pode passar a apontar para outra imagem, o digest não.
# O Dependabot (.github/dependabot.yml) propõe a atualização do digest quando sai uma versão nova.
#
# Nenhum segredo entra na imagem: senha do banco e chave de cifragem chegam por variável de ambiente ao subir o
# container (ver compose.yaml). Os testes NÃO rodam aqui: eles precisam de Docker (Testcontainers) e já rodam no CI.

# ---------- Estágio 1: build ----------
FROM eclipse-temurin:25-jdk-alpine@sha256:22edbb1021420ae6e248519d1af07577742314d67d4c60fcbb2e285b7e47fa78 AS build
WORKDIR /workspace

# Primeiro só o que define as dependências: enquanto o pom.xml não mudar, o Docker reaproveita esta camada
# (dependências já baixadas) e uma mudança no código não baixa a internet de novo.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -ntp -DskipTests package \
    && cp target/moneyx-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- Estágio 2: runtime ----------
FROM eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89 AS runtime

# Usuário e grupo dedicados, sem senha, sem shell de login, com UID/GID numéricos fixos (10001): orquestradores
# como o Kubernetes conseguem provar que o container não roda como root ("runAsNonRoot").
RUN addgroup -S -g 10001 moneyx && adduser -S -D -H -u 10001 -G moneyx -s /sbin/nologin moneyx

WORKDIR /app

# O .jar "explodido" em camadas, da que menos muda para a que mais muda: dependências de terceiros quase nunca mudam,
# então só a última camada (nosso código) é reenviada a cada nova versão. Arquivos pertencem ao root (somente leitura
# para o usuário moneyx): a aplicação não consegue alterar o próprio código.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001

# Memória da JVM proporcional ao limite do container (o padrão da JVM é só 25%).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# 8080 = API. A 8081 (Actuator) ouve só em 127.0.0.1 DENTRO do container: não é publicada nem acessível de fora.
EXPOSE 8080

# Saúde verificada de dentro do container, na porta de gerenciamento. "start-period" cobre a subida + migrations.
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8081/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
