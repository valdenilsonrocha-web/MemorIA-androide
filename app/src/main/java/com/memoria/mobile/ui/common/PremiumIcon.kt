package com.memoria.mobile.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.memoria.mobile.R

/**
 * O escudo com a coroa dourada que marca o Premium, como na versão web.
 *
 * A web usa `premium-icon.svg` em dez sítios; a Mobile usava o
 * `Icons.Filled.WorkspacePremium` do Material, que é um símbolo genérico e sem
 * relação com a marca. Este componente traz o mesmo desenho para o telemóvel.
 *
 * É [Image] e não [androidx.compose.material3.Icon] de propósito: o ícone é
 * multicolor (azul, branco e dourado) e o `Icon` aplicaria um tint único que o
 * achataria numa silhueta só.
 */
@Composable
fun PremiumIcon(
    modifier: Modifier = Modifier,
    size: Dp = PremiumIconSize.Inline,
) {
    Image(
        painter = painterResource(R.drawable.ic_premium),
        contentDescription = null, // decorativo: o texto ao lado já diz o que é
        modifier = modifier.size(size),
    )
}

/**
 * Os tamanhos que a web define em `main.css`.
 *
 * Os valores de lá são `clamp(min, vw, max)` e no telemóvel a conta cai
 * praticamente sempre no mínimo, que é o que está aqui.
 */
object PremiumIconSize {
    /** `--premium-icon-inline-size` — junto a um texto ou dentro de um botão. */
    val Inline: Dp = 32.dp

    /** `--premium-icon-heading-size` — ao lado de um título de secção. */
    val Heading: Dp = 44.dp

    /** `--premium-icon-lock-size` — conteúdo bloqueado por falta de plano. */
    val Lock: Dp = 62.dp

    /** `--premium-icon-empty-size` — estado vazio. */
    val Empty: Dp = 76.dp
}
