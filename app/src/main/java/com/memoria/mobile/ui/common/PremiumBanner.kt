package com.memoria.mobile.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memoria.mobile.R

/**
 * O convite para assinar o Premium, como na versão web.
 *
 * É a `<section class="ad-banner ad-only-free updates-premium-banner">` do
 * `index.html`: imagem, título, descrição e o botão que leva aos planos. O
 * `ad-only-free` diz o essencial — **só aparece a quem não é Premium**, e é por
 * isso que quem chama isto tem de verificar o plano antes.
 *
 * O texto é o mesmo da web, palavra por palavra, para as duas versões
 * prometerem exactamente a mesma coisa a quem paga.
 */
@Composable
fun PremiumBanner(onOpenPlans: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.updates_banner),
                contentDescription = "Tela de assinatura do aplicativo MemórIA",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .heightIn(max = 180.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x140F172A), RoundedCornerShape(14.dp)),
            )

            Text(
                "Assine o MemórIA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                "Tenha tudo do nosso aplicativo em suas mãos, sem anúncios, medicamentos " +
                    "ilimitados, aviso aos cuidadores, consultas e médicos, e muito mais.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Button(onClick = onOpenPlans, modifier = Modifier.fillMaxWidth()) {
                PremiumIcon()
                Text("  Assine agora")
            }
        }
    }
}
