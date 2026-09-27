package fluxai.app

import java.text.Normalizer

// =========================================================================
// SUGESTÃO DE CATEGORIA PELA DESCRIÇÃO
// Usada na leitura de notificações do banco e na importação de extrato.
// Só devolve categorias padrão do app; o que não reconhece vira "Outros".
// =========================================================================
val CategoriasPadrao = listOf("Moradia", "Alimentação", "Transporte", "Saúde", "Educação", "Lazer", "Empréstimo", "Cartão de Crédito", "Outros")

private val palavrasPorCategoria: List<Pair<String, List<String>>> = listOf(
    "Alimentação" to listOf(
        "ifood", "rappi", "restaurante", "lanchonete", "padaria", "panificadora", "supermercado", "mercado", "atacadao", "atacadista",
        "assai", "carrefour", "pao de acucar", "extra ", "burger", "mcdonald", "habib", "pizza", "acougue", "hortifruti", "sorveteria",
        "cafeteria", "starbucks", "ze delivery", "subway", "outback", "spoleto", "giraffas", "bobs", "kfc", "lanches", "confeitaria"
    ),
    "Transporte" to listOf(
        "uber", "99app", "99 pop", "99pop", "cabify", "posto", "combustivel", "gasolina", "shell", "ipiranga", "petrobras", "br mania",
        "estacionamento", "estapar", "pedagio", "sem parar", "veloe", "conectcar", "metro", "onibus", "bilhete unico", "cptm", "detran", "ipva"
    ),
    "Saúde" to listOf(
        "farmacia", "drogaria", "droga raia", "drogasil", "pague menos", "pacheco", "panvel", "hospital", "clinica", "laboratorio",
        "medic", "odonto", "dentista", "unimed", "amil", "hapvida", "sulamerica", "otica", "fisioterap", "psicolog", "exame"
    ),
    "Educação" to listOf("escola", "colegio", "faculdade", "universidade", "curso", "udemy", "alura", "livraria", "papelaria", "duolingo", "mensalidade escolar"),
    "Lazer" to listOf(
        "netflix", "spotify", "disney", "prime video", "hbo", "globoplay", "deezer", "youtube", "cinema", "cinemark", "ingresso",
        "steam", "playstation", "xbox", "nintendo", "show", "teatro", "parque", "airbnb", "hotel", "booking", "decolar", "latam", "gol linhas", "azul linhas"
    ),
    "Moradia" to listOf(
        "aluguel", "condominio", "energia", "enel", "cemig", "copel", "light s", "coelba", "celpe", "equatorial", "cpfl", "sabesp", "copasa",
        "saneamento", "agua e esgoto", "comgas", "gas natural", "internet", "vivo", "claro", "tim ", "oi fibra", "net servicos", "leroy", "telhanorte"
    ),
    "Empréstimo" to listOf("emprestimo", "financiamento", "consignado", "parcela contrato")
)

private fun semAcento(texto: String): String =
    Normalizer.normalize(texto, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

fun sugerirCategoria(descricao: String): String {
    val t = " " + semAcento(descricao) + " "
    return palavrasPorCategoria.firstOrNull { (_, palavras) -> palavras.any { it in t } }?.first ?: "Outros"
}
