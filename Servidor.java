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

            // Verificação simplificada: só verifica se o aluno e o livro existem
            Aluno aluno = alunos.stream().filter(a -> a.matricula.equals(dados.get("matricula"))).findFirst().orElse(null);
            Livro livro = livros.stream().filter(l -> l.titulo.equals(dados.get("titulo"))).findFirst().orElse(null);

            if (aluno == null || livro == null || livro.quantidade <= 0) {
                responder(exchange, "Erro: aluno ou livro não encontrado ou livro indisponível.");
                return;
            }

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                Date dataEmprestimo = sdf.parse(dados.get("dataEmprestimo"));
                Date dataDevolucao = sdf.parse(dados.get("dataDevolucao"));

                emprestimos.add(new Emprestimo(dados.get("matricula"), dados.get("titulo"), dataEmprestimo, dataDevolucao, false));
                livro.quantidade--;  // Decrementa a quantidade do livro
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
                    e.devolvido = true;  // Marca como devolvido
                    livros.stream().filter(l -> l.titulo.equals(e.titulo)).findFirst().ifPresent(l -> l.quantidade++);  // Devolve o livro
                    break;
                }
            }

            redirecionar(exchange, "/livros-emprestados.html");
        }
    }

    static void listarAlunos(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(cabecalhoHtml("Lista de Alunos", "fa-user-graduate"));
        html.append("<ul>");
        alunos.forEach(a -> html.append("<li>").append(a.nome).append(" - ").append(a.matricula).append(" (").append(a.turma).append(")</li>"));
        html.append("</ul>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarLivros(HttpExchange exchange) throws IOException {
        StringBuilder html = new StringBuilder(cabecalhoHtml("Lista de Livros", "fa-book"));
        html.append("<ul>");
        livros.forEach(l -> html.append("<li>").append(l.titulo).append(" - ").append(l.autor)
                .append(" (").append(l.quantidade).append(" disponíveis)</li>"));
        html.append("</ul>").append(botaoVoltar()).append(rodapeHtml());
        responder(exchange, html.toString());
    }

    static void listarEmprestimos(HttpExchange exchange) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        StringBuilder html = new StringBuilder(cabecalhoHtml("Livros Emprestados", "fa-book-reader"));
        html.append("<table class='table table-bordered'><tr><th>Matrícula</th><th>Livro</th><th>Data Empréstimo</th><th>Data Devolução</th></tr>");
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
        html.append("<table class='table table-bordered'><tr><th>Matrícula</th><th>Livro</th><th>Data Devolução</th></tr>");
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
               "<link href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css' rel='stylesheet'></head><body class='container mt-5'><h2 class='mb-4'><i class='fas " + icone + "'></i> " + titulo + "</h2>";
    }

    static String botaoVoltar() {
        return "<a href='/' class='btn btn-secondary mt-3'><i class='fas fa-arrow-left'></i> Voltar</a>";
    }

    static String rodapeHtml() {
        return "<footer class='mt-4'><p>&copy; 2025 - Sistema Biblioteca</p></footer></body></html>";
    }

    static void responder(HttpExchange exchange, String resposta) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, resposta.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(resposta.getBytes());
        os.close();
    }

    static void redirecionar(HttpExchange exchange, String url) throws IOException {
        exchange.getResponseHeaders().set("Location", url);
        exchange.sendResponseHeaders(302, -1);
    }

    static Map<String, String> lerFormulario(HttpExchange exchange) throws IOException {
        Map<String, String> dados = new HashMap<>();
        InputStream is = exchange.getRequestBody();
        String query = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Arrays.stream(query.split("&")).forEach(par -> {
            String[] keyValue = par.split("=");
            dados.put(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
        });
        return dados;
    }
}

class Aluno {
    String nome, matricula, turma;

    Aluno(String nome, String matricula, String turma) {
        this.nome = nome;
        this.matricula = matricula;
        this.turma = turma;
    }
}

class Livro {
    String titulo, autor;
    int quantidade;

    Livro(String titulo, String autor, int quantidade) {
        this.titulo = titulo;
        this.autor = autor;
        this.quantidade = quantidade;
    }
}

class Emprestimo {
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
