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

        // Verifica se o aluno existe
        boolean alunoExiste = alunos.stream()
                                    .anyMatch(a -> a.matricula.equals(dados.get("matricula")));
        
        // Verifica se o livro existe e se há quantidade disponível
        Livro livro = livros.stream()
                            .filter(l -> l.titulo.equals(dados.get("titulo")))
                            .findFirst()
                            .orElse(null);

        if (!alunoExiste) {
            responder(exchange, "Erro: Aluno não encontrado.");
            return;
        }
        
        if (livro == null) {
            responder(exchange, "Erro: Livro não encontrado.");
            return;
        }
        
        if (livro.quantidade <= 0) {
            responder(exchange, "Erro: Livro indisponível.");
            return;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date dtEmp = sdf.parse(dados.get("dataEmprestimo"));
            Date dtDev = sdf.parse(dados.get("dataDevolucao"));
            
            // Registra o empréstimo
            emprestimos.add(new Emprestimo(dados.get("matricula"), dados.get("titulo"), dtEmp, dtDev, false));
            livro.quantidade--; // Decrementa a quantidade de livros disponíveis
            
            redirecionar(exchange, "/livros-emprestados.html");
        } catch (Exception e) {
            responder(exchange, "Erro ao registrar o empréstimo: " + e.getMessage());
        }
    }

    static void registrarDevolucao(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) return;
        
        // Lê os dados do formulário
        Map<String, String> dados = lerFormulario(exchange);

        boolean devolvidoComSucesso = false;
        
        // Percorre todos os empréstimos para encontrar aquele que corresponde ao aluno e livro
        for (Emprestimo e : emprestimos) {
            if (!e.devolvido && e.matricula.equals(dados.get("matricula")) && e.titulo.equals(dados.get("titulo"))) {
                e.devolvido = true; // Marca como devolvido
                // Aumenta a quantidade do livro
                livros.stream()
                    .filter(l -> l.titulo.equals(e.titulo))
                    .findFirst()
                    .ifPresent(l -> l.quantidade++);
                devolvidoComSucesso = true;
                break;
            }
        }
        
        // Se não foi possível encontrar o empréstimo correspondente
        if (!devolvidoComSucesso) {
            responder(exchange, "Erro: Empréstimo não encontrado para o aluno ou livro fornecido.");
            return;
        }
        
        // Redireciona para a lista de livros emprestados após a devolução
        redirecionar(exchange, "/livros-emprestados.html");
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

    // Métodos auxiliares

    static Map<String, String> lerFormulario(HttpExchange exchange) throws IOException {
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder dados = new StringBuilder();
        String linha;
        while ((linha = br.readLine()) != null) {
            dados.append(linha);
        }
        return parseQueryString(dados.toString());
    }

    static Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        for (String p : query.split("&")) {
            String[] keyValue = p.split("=");
            if (keyValue.length == 2) {
                params.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
            }
        }
        return params;
    }

    static void responder(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    static void redirecionar(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    static String cabecalhoHtml(String titulo, String icone) {
        return "<html><head><meta charset='UTF-8'><title>" + titulo + "</title><link rel='stylesheet' href='/style.css'></head><body>"
            + "<h1><i class='fa " + icone + "'></i> " + titulo + "</h1>";
    }

    static String botaoVoltar() {
        return "<br><a href='/index.html' class='btn btn-primary'>Voltar</a>";
    }

    static String rodapeHtml() {
        return "</body></html>";
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
