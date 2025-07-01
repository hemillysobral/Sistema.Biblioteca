import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Servidor {
    static List<Aluno> alunos = new ArrayList<>();
    static List<Livro> livros = new ArrayList<>();
    static List<Emprestimo> emprestimos = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> servirArquivo(exchange, "index.html"));
        server.createContext("/cadastraraluno.html", exchange -> servirArquivo(exchange, "cadastraraluno.html"));
        server.createContext("/cadastrarlivro.html", exchange -> servirArquivo(exchange, "cadastrarlivro.html"));
        server.createContext("/emprestimo.html", exchange -> servirArquivo(exchange, "emprestimo.html"));
        server.createContext("/devolucao.html", exchange -> servirArquivo(exchange, "devolucao.html"));
        server.createContext("/lista-alunos.html", Servidor::listarAlunos);
        server.createContext("/lista-livros.html", Servidor::listarLivros);
        server.createContext("/livros-emprestados.html", Servidor::listarEmprestimos);
        server.createContext("/livros-atrasados.html", Servidor::listarAtrasados);

        // Servir CSS
        server.createContext("/style.css", exchange -> {
            byte[] bytes = Files.readAllBytes(Paths.get("style.css"));
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        // Rotas POST
        server.createContext("/cadastrar-aluno", Servidor::cadastrarAluno);
        server.createContext("/cadastrar-livro", Servidor::cadastrarLivro);
        server.createContext("/emprestar-livro", Servidor::registrarEmprestimo);
        server.createContext("/devolver-livro", Servidor::registrarDevolucao);

        server.setExecutor(null);
        System.out.println("Servidor rodando na porta " + port);
        server.start();
    }

    static void servirArquivo(HttpExchange exchange, String nomeArquivo) throws IOException {
        String html = new String(Files.readAllBytes(Paths.get(nomeArquivo)), StandardCharsets.UTF_8);
        responder(exchange, html);
    }

    static void cadastrarAluno(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) return;
        Map<String, String> dados = lerFormulario(exchange);
        alunos.add(new Aluno(dados.get("nome"), dados.get("matricula"), dados.get("turma")));
        redirecionar(exchange, "/lista-alunos.html");
    }

    static void cadastrarLivro(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) return;
        Map<String, String> dados = lerFormulario(exchange);
        livros.add(new Livro(dados.get("titulo"), dados.get("autor"), Integer.parseInt(dados.get("quantidade"))));
        redirecionar(exchange, "/lista-livros.html");
    }

    static void registrarEmprestimo(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) return;
        Map<String, String> dados = lerFormulario(exchange);

        boolean alunoExiste = alunos.stream().anyMatch(a -> a.matricula.equals(dados.get("matricula")));
        Livro livro = livros.stream().filter(l -> l.titulo.equals(dados.get("titulo"))).findFirst().orElse(null);

        if (!alunoExiste || livro == null) {
            responder(exchange, "Erro: aluno ou livro não encontrado.");
            return;
        }
        if (livro.quantidade <= 0) {
            responder(exchange, "Erro: livro indisponível.");
            return;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date dtEmp = sdf.parse(dados.get("dataEmprestimo"));
            Date dtDev = sdf.parse(dados.get("dataDevolucao"));
            emprestimos.add(new Emprestimo(dados.get("matricula"), dados.get("titulo"), dtEmp, dtDev, false));
            livro.quantidade--;
            redirecionar(exchange, "/livros-emprestados.html");
        } catch (Exception e) {
            responder(exchange, "Erro ao registrar: " + e.getMessage());
        }
    }

    static void registrarDevolucao(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) return;
        Map<String, String> dados = lerFormulario(exchange);

        boolean devolvido = false;

        for (Emprestimo e : emprestimos) {
            if (!e.devolvido && e.matricula.equals(dados.get("matricula")) && e.titulo.equals(dados.get("titulo"))) {
                e.devolvido = true;
                livros.stream().filter(l -> l.titulo.equals(e.titulo))
                      .findFirst().ifPresent(l -> l.quantidade++);
                devolvido = true;
                break;
            }
        }

        if (devolvido) {
            redirecionar(exchange, "/livros-emprestados.html");
        } else {
            responder(exchange, "Erro: empréstimo não encontrado ou já devolvido.");
        }
    }

    static void listarAlunos(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(cabecalhoHtml("Lista de Alunos", "fa-user-graduate"));
        html.append("<ul>");
        alunos.forEach(a -> html.append("<li>").append(a.nome).append(" – ").append(a.matricula).append(" (").append(a.turma).append(")</li>"));
        html.append("</ul>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarLivros(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(cabecalhoHtml("Lista de Livros", "fa-book"));
        html.append("<ul>");
        livros.forEach(l -> html.append("<li>").append(l.titulo).append(" – ").append(l.autor).append(" (").append(l.quantidade).append(" disponíveis)</li>"));
        html.append("</ul>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarEmprestimos(HttpExchange exchange) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        StringBuilder html = new StringBuilder(cabecalhoHtml("Livros Emprestados", "fa-book-reader"));
        html.append("<table class='table table-bordered'><tr><th>Matrícula</th><th>Livro</th><th>Empr.</th><th>Devolução</th></tr>");
        emprestimos.stream().filter(e -> !e.devolvido).forEach(e ->
            html.append("<tr><td>").append(e.matricula).append("</td><td>").append(e.titulo)
                .append("</td><td>").append(sdf.format(e.dataEmprestimo))
                .append("</td><td>").append(sdf.format(e.dataDevolucao)).append("</td></tr>")
        );
        html.append("</table>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarAtrasados(HttpExchange exchange) throws IOException {
        Date hoje = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        StringBuilder html = new StringBuilder(cabecalhoHtml("Livros Atrasados", "fa-clock"));
        html.append("<table class='table table-bordered'><tr><th>Matrícula</th><th>Livro</th><th>Devolução</th></tr>");
        emprestimos.stream()
            .filter(e -> !e.devolvido && e.dataDevolucao.before(hoje))
            .forEach(e -> html.append("<tr class='text-danger fw-bold'><td>").append(e.matricula)
                .append("</td><td>").append(e.titulo)
                .append("</td><td>").append(sdf.format(e.dataDevolucao))
                .append("</td></tr>")
            );
        html.append("</table>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static String cabecalhoHtml(String titulo, String icone) {
        return "<!DOCTYPE html><html lang='pt-br'><head><meta charset='UTF-8'><title>" + titulo +
               "</title><link href='https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css' rel='stylesheet'>" +
               "<link href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css' rel='stylesheet'>" +
               "<link href='/style.css' rel='stylesheet'></head><body class='container mt-5'><h2 class='mb-4'><i class='fas " + icone + "'></i> " + titulo + "</h2>";
    }

    static String botaoVoltar() {
        return "<a href='/' class='btn btn-secondary mt-3'><i class='fas fa-arrow-left'></i> Voltar</a>";
    }

    static String rodapeHtml() {
        return "</body></html>";
    }

    static Map<String, String> lerFormulario(HttpExchange exchange) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        String linha = br.readLine();
        Map<String, String> map = new HashMap<>();
        if (linha != null) {
            for (String par : linha.split("&")) {
                String[] p = par.split("=");
                if (p.length == 2) {
                    map.put(URLDecoder.decode(p[0], StandardCharsets.UTF_8), URLDecoder.decode(p[1], StandardCharsets.UTF_8));
                }
            }
        }
        return map;
    }

    static void redirecionar(HttpExchange exchange, String destino) throws IOException {
        exchange.getResponseHeaders().set("Location", destino);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    static void responder(HttpExchange exchange, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    static class Aluno {
        String nome, matricula, turma;
        Aluno(String nome, String matricula, String turma) {
            this.nome = nome; this.matricula = matricula; this.turma = turma;
        }
    }

    static class Livro {
        String titulo, autor; int quantidade;
        Livro(String titulo, String autor, int quantidade) {
            this.titulo = titulo; this.autor = autor; this.quantidade = quantidade;
        }
    }

    static class Emprestimo {
        String matricula, titulo; Date dataEmprestimo, dataDevolucao; boolean devolvido;
        Emprestimo(String matricula, String titulo, Date dataEmprestimo, Date dataDevolucao, boolean devolvido) {
            this.matricula = matricula; this.titulo = titulo;
            this.dataEmprestimo = dataEmprestimo; this.dataDevolucao = dataDevolucao;
            this.devolvido = devolvido;
        }
    }
}
