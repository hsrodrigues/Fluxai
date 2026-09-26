package fluxai.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.QuerySnapshot

// =========================================================================
// ÍCONES DAS CATEGORIAS
// A chave (String) é o que fica salvo no Firebase, no campo "icone" de categorias_custom
// =========================================================================
val CatalogoIconesCategoria: List<Pair<String, ImageVector>> = listOf(
    "casa" to Icons.Default.Home,
    "restaurante" to Icons.Default.Restaurant,
    "mercado" to Icons.Default.ShoppingCart,
    "cafe" to Icons.Default.LocalCafe,
    "bar" to Icons.Default.LocalBar,
    "carro" to Icons.Default.DirectionsCar,
    "combustivel" to Icons.Default.LocalGasStation,
    "onibus" to Icons.Default.DirectionsBus,
    "moto" to Icons.Default.TwoWheeler,
    "aviao" to Icons.Default.Flight,
    "saude" to Icons.Default.MedicalServices,
    "farmacia" to Icons.Default.LocalPharmacy,
    "academia" to Icons.Default.FitnessCenter,
    "educacao" to Icons.Default.School,
    "livro" to Icons.AutoMirrored.Filled.MenuBook,
    "lazer" to Icons.Default.SportsEsports,
    "filme" to Icons.Default.Movie,
    "musica" to Icons.Default.MusicNote,
    "pet" to Icons.Default.Pets,
    "bebe" to Icons.Default.ChildCare,
    "roupa" to Icons.Default.Checkroom,
    "beleza" to Icons.Default.Spa,
    "presente" to Icons.Default.CardGiftcard,
    "compras" to Icons.Default.ShoppingBag,
    "celular" to Icons.Default.PhoneAndroid,
    "internet" to Icons.Default.Wifi,
    "energia" to Icons.Default.Bolt,
    "agua" to Icons.Default.WaterDrop,
    "manutencao" to Icons.Default.Build,
    "trabalho" to Icons.Default.Work,
    "dinheiro" to Icons.Default.MonetizationOn,
    "cartao" to Icons.Default.CreditCard,
    "banco" to Icons.Default.AccountBalance,
    "poupanca" to Icons.Default.Savings,
    "doacao" to Icons.Default.VolunteerActivism,
    "viagem" to Icons.Default.Luggage,
    "assinatura" to Icons.Default.Subscriptions,
    "impostos" to Icons.Default.Receipt,
    "outros" to Icons.Default.Category,
    "estrela" to Icons.Default.Star
)

private val mapaIcones = CatalogoIconesCategoria.toMap()

// Categorias padrão do app: ícone e cor fixos
private val categoriasPadrao: Map<String, Pair<String, Color>> = mapOf(
    "Moradia" to ("casa" to Color(0xFF2196F3)),
    "Alimentação" to ("restaurante" to Color(0xFFFF9800)),
    "Transporte" to ("carro" to Color(0xFF673AB7)),
    "Saúde" to ("saude" to Color(0xFF4CAF50)),
    "Educação" to ("educacao" to Color(0xFF00ACC1)),
    "Lazer" to ("lazer" to Color(0xFFFFC107)),
    "Empréstimo" to ("dinheiro" to Color(0xFF795548)),
    "Cartão de Crédito" to ("cartao" to Color(0xFF009688)),
    "Manutenção" to ("manutencao" to Color(0xFF8D6E63)),
    "Outros" to ("outros" to Color(0xFF607D8B))
)
private val corCategoriaCustom = Color(0xFFE91E63)

// Nome da categoria personalizada -> chave do ícone escolhido. Preenchido pelas telas que escutam o Firebase.
val IconesCategoriasCustom = mutableStateMapOf<String, String>()

fun atualizarIconesCustom(snap: QuerySnapshot) {
    val novos = snap.documents.mapNotNull { d ->
        val nome = d.getString("nome") ?: return@mapNotNull null
        nome to (d.getString("icone") ?: "estrela")
    }.toMap()
    IconesCategoriasCustom.keys.retainAll(novos.keys)
    IconesCategoriasCustom.putAll(novos)
}

fun iconePorChave(chave: String?): ImageVector = mapaIcones[chave] ?: Icons.Default.Star

fun iconeCategoria(categoria: String): ImageVector =
    iconePorChave(categoriasPadrao[categoria]?.first ?: IconesCategoriasCustom[categoria])

fun corCategoria(categoria: String): Color = categoriasPadrao[categoria]?.second ?: corCategoriaCustom

// Grade para escolher o ícone de uma categoria
@Composable
fun SeletorIconeCategoria(selecionado: String, corDestaque: Color, corIcone: Color, onSelecionar: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(44.dp),
        modifier = modifier.heightIn(max = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(CatalogoIconesCategoria) { (chave, icone) ->
            val ativo = chave == selecionado
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (ativo) corDestaque.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(12.dp))
                    .border(1.dp, if (ativo) corDestaque else corIcone.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .clickable { onSelecionar(chave) },
                contentAlignment = Alignment.Center
            ) {
                Icon(icone, contentDescription = chave, tint = if (ativo) corDestaque else corIcone, modifier = Modifier.size(22.dp))
            }
        }
    }
}
