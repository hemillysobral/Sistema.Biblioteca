# Etapa 1: Compilar o código Java
FROM openjdk:17 AS build

WORKDIR /app

# Copia os arquivos fonte e HTML/CSS
COPY . .

# Compila o Servidor.java
RUN javac Servidor.java

# Etapa 2: Imagem final para execução
FROM openjdk:17

WORKDIR /app

# Copia os arquivos compilados e recursos da etapa anterior
COPY --from=build /app /app

# Define a variável de ambiente usada pelo servidor
ENV PORT=8080

# Expõe a porta usada pela aplicação
EXPOSE 8080

# Comando para rodar o servidor
CMD ["java", "Servidor"]
