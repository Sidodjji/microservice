param([Parameter(Mandatory=$true)][string]$MonolithPath)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$source=(Resolve-Path -LiteralPath $MonolithPath).Path
$reference=Join-Path $projectRoot 'target/monolith-reference'
if(-not (Test-Path -LiteralPath (Join-Path $source 'pom.xml'))) { throw 'Monolith pom.xml is missing' }
New-Item -ItemType Directory -Force $reference | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'src') -Destination $reference -Recurse -Force
Copy-Item -LiteralPath (Join-Path $source 'pom.xml') -Destination $reference -Force
@"
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -DskipTests package
FROM eclipse-temurin:17-jre-alpine
COPY --from=build /workspace/target/car_sharing_module_monolith-0.0.1-SNAPSHOT.jar /app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
"@ | Set-Content (Join-Path $reference 'Dockerfile') -Encoding utf8
@"
name: carsharing-reference-test
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: car_sharing
      POSTGRES_USER: car_sharing
      POSTGRES_PASSWORD: car_sharing
    healthcheck:
      test: [CMD-SHELL, pg_isready -U car_sharing]
      interval: 2s
      retries: 30
  monolith:
    build: .
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/car_sharing
      DB_USERNAME: car_sharing
      DB_PASSWORD: car_sharing
    ports: ['127.0.0.1:18080:8080']
    depends_on:
      postgres:
        condition: service_healthy
"@ | Set-Content (Join-Path $reference 'compose.yaml') -Encoding utf8
docker compose -f (Join-Path $reference 'compose.yaml') up -d --build
if($LASTEXITCODE -ne 0) { throw 'Reference stack failed to start' }
$ready=$false
for($attempt=0;$attempt -lt 60;$attempt++) {
    try {
        $null=Invoke-WebRequest 'http://127.0.0.1:18080/api/cars' -UseBasicParsing -TimeoutSec 3
        $ready=$true
        break
    } catch { Start-Sleep -Seconds 1 }
}
if(-not $ready) { throw 'Reference API did not become ready' }
Write-Output 'Unmodified reference copy is ready at http://127.0.0.1:18080'