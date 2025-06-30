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
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> servirArquivo(exchange, "index.html"));
        server.createContext("/cadastraraluno.html", exchange -> servirArquivo(exchange, "cadastraraluno.html"));
        server.createContext("/cadastrarlivro.html", exchange -> servirArquivo(exchange, "cadastrarlivro.html"));
        server.createContext("/emprestimo.html", exchange -> servirArquivo(exchange, "emprestimo.html"));
        server.createContext("/devolucao.html", exchange -> servirArquivo(exchange, "devolucao.html"));
        server.createContext("/lista-alunos.html", Servidor::listarAlunos);
        server.createContext("/lista-livros.html", Servidor::listarLivros);
        server.createContext("/livros-emprestados.html", Servidor::listarEmprestimos);
        server.createContext("/livros-atrasados.html", Servidor::listarAtrasados);

        server.createContext("/cadastrar-aluno", Servidor::cadastrarAluno);
        server.createContext("/cadastrar-livro", Servidor::cadastrarLivro);
        server.createContext("/emprestar-livro", Servidor::registrarEmprestimo);
        server.createContext("/devolver-livro", Servidor::registrarDevolucao);

        server.setExecutor(null);
        System.out.println("Servidor rodando em http://localhost:8080");
        server.start();
    }

    static void servirArquivo(HttpExchange exchange, String nomeArquivo) throws IOException {
        String html = new String(Files.readAllBytes(Paths.get(nomeArquivo)), StandardCharsets.UTF_8);
        responder(exchange, html);
    }

    static void cadastrarAluno(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, String> dados = lerFormulario(exchange);
            alunos.add(new Aluno(dados.get("nome"), dados.get("matricula"), dados.get("turma")));
            redirecionar(exchange, "/lista-alunos.html");
        }
    }

    static void cadastrarLivro(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, String> dados = lerFormulario(exchange);
            livros.add(new Livro(dados.get("titulo"), dados.get("autor"), Integer.parseInt(dados.get("quantidade"))));
            redirecionar(exchange, "/lista-livros.html");
        }
    }

    static void registrarEmprestimo(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, String> dados = lerFormulario(exchange);
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date dataEmprestimo = sdf.parse(dados.get("dataEmprestimo"));
                Date dataDevolucao = sdf.parse(dados.get("dataDevolucao"));

                // REGISTRA O EMPRÉSTIMO SIMPLES SEM VERIFICAR DISPONIBILIDADE
                emprestimos.add(new Emprestimo(dados.get("matricula"), dados.get("titulo"), dataEmprestimo, dataDevolucao, false));
                redirecionar(exchange, "/livros-emprestados.html");
            } catch (Exception e) {
                responder(exchange, "Erro ao registrar empréstimo: " + e.getMessage());
            }
        }
    }

    static void registrarDevolucao(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            Map<String, String> dados = lerFormulario(exchange);
            for (Emprestimo e : emprestimos) {
                if (!e.devolvido && e.matricula.equals(dados.get("matricula")) && e.titulo.equals(dados.get("titulo"))) {
                    e.devolvido = true;
                    break;
                }
            }
            redirecionar(exchange, "/livros-emprestados.html");
        }
    }

    static void listarAlunos(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(cabecalhoHtml("Lista de Alunos", "fa-user-graduate"));
        html.append("<ul>");
        for (Aluno a : alunos) {
            html.append("<li>").append(a.nome).append(" - ")
                .append(a.matricula).append(" (").append(a.turma).append(")</li>");
        }
        html.append("</ul>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarLivros(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(cabecalhoHtml("Lista de Livros", "fa-book"));
        html.append("<ul>");
        for (Livro l : livros) {
            html.append("<li>").append(l.titulo).append(" - ").append(l.autor)
                .append(" (").append(l.quantidade).append(" disponíveis)</li>");
        }
        html.append("</ul>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarEmprestimos(HttpExchange exchange) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        StringBuilder html = new StringBuilder(cabecalhoHtml("Livros Emprestados", "fa-book-reader"));
        html.append("<table class='table table-bordered'><tr><th>Matrícula</th><th>Livro</th><th>Data Empréstimo</th><th>Data Devolução</th></tr>");
        for (Emprestimo e : emprestimos) {
            if (!e.devolvido) {
                html.append("<tr><td>").append(e.matricula).append("</td><td>").append(e.titulo)
                    .append("</td><td>").append(sdf.format(e.dataEmprestimo))
                    .append("</td><td>").append(sdf.format(e.dataDevolucao)).append("</td></tr>");
            }
        }
        html.append("</table>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarAtrasados(HttpExchange exchange) throws IOException {
        Date hoje = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        StringBuilder html = new StringBuilder(cabecalhoHtml("Livros Atrasados", "fa-clock"));
        html.append("<table class='table table-bordered'><tr><th>Matrícula</th><th>Livro</th><th>Data Devolução</th></tr>");
        for (Emprestimo e : emprestimos) {
            if (!e.devolvido && e.dataDevolucao.before(hoje)) {
                html.append("<tr class='text-danger fw-bold'><td>").append(e.matricula).append("</td><td>")
                    .append(e.titulo).append("</td><td>").append(sdf.format(e.dataDevolucao)).append("</td></tr>");
            }
        }
        html.append("</table>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static String cabecalhoHtml(String titulo, String icone) {
        return """
        <!DOCTYPE html>
        <html lang="pt-br">
        <head>
            <meta charset="UTF-8">
            <title>%s</title>
            <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
            <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
        </head>
        <body class="container mt-5">
        <h2 class="mb-4"><i class="fas %s"></i> %s</h2>
        """.formatted(titulo, icone, titulo);
    }

    static String botaoVoltar() {
        return "<a href='/' class='btn btn-secondary mt-3'><i class='fas fa-arrow-left'></i> Voltar</a>";
    }

    static String rodapeHtml() {
        return "</body></html>";
    }

    static Map<String, String> lerFormulario(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(isr);
        String formData = reader.readLine();

        Map<String, String> params = new HashMap<>();
        if (formData != null) {
            String[] pares = formData.split("&");
            for (String par : pares) {
                String[] chaveValor = par.split("=");
                if (chaveValor.length == 2) {
                    String chave = URLDecoder.decode(chaveValor[0], StandardCharsets.UTF_8);
                    String valor = URLDecoder.decode(chaveValor[1], StandardCharsets.UTF_8);
                    params.put(chave, valor);
                }
            }
        }
        return params;
    }

    static void responder(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    static void redirecionar(HttpExchange exchange, String url) throws IOException {
        exchange.getResponseHeaders().set("Location", url);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    static class Aluno {
        String nome, matricula, turma;
        Aluno(String nome, String matricula, String turma) {
            this.nome = nome;
            this.matricula = matricula;
            this.turma = turma;
        }
    }

    static class Livro {
        String titulo, autor;
        int quantidade;
        Livro(String titulo, String autor, int quantidade) {
            this.titulo = titulo;
            this.autor = autor;
            this.quantidade = quantidade;
        }
    }

    static class Emprestimo {
        String matricula, titulo;
        Date dataEmprestimo, dataDevolucao;
        boolean devolvido;
        Emprestimo(String matricula, String titulo, Date dataEmprestimo, Date dataDevolucao, boolean devolvido) {
            this.matricula = matricula;
            this.titulo = titulo;
            this.dataEmprestimo = dataEmprestimo;
            this.dataDevolucao = dataDevolucao;
            this.devolvido = devolvido;
        }
    }
}
