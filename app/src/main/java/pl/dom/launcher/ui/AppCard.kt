package pl.dom.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import pl.dom.launcher.data.TvApp

private val CardShape = RoundedCornerShape(9.dp)

/** Kafelek aplikacji z jej oficjalnym banerem. Gdy aplikacja nie ma banera – ikona i nazwa. */
@Composable
fun AppCard(
    app: TvApp,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    moving: Boolean = false,
    /** Ukryta (widoczna tylko przy „Pokazuj ukryte”) – przygaszona, ze znaczkiem. */
    hidden: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape = CardShape),
        colors = CardDefaults.colors(containerColor = Palette.Card, focusedContainerColor = Palette.Card),
        scale = CardDefaults.scale(focusedScale = if (moving) 1.14f else 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(if (moving) 3.dp else 2.dp, Palette.Amber), shape = CardShape)),
    ) {
        Box(Modifier.fillMaxSize().alpha(if (hidden) 0.4f else 1f)) {
            val banner = app.banner
            if (banner != null) {
                Image(
                    bitmap = banner,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(26.dp)) }
                    Text(
                        app.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.Text,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hidden) {
                Text(
                    "ukryta",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    fontSize = 7.sp, color = Palette.Soft,
                )
            }
            if (moving) {
                Box(
                    Modifier.fillMaxSize().background(Palette.Bg.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("◀   ▶", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Amber)
                }
            }
            if (isFavorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp)
                        .background(Palette.Bg.copy(alpha = 0.75f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Star, contentDescription = "W ulubionych", tint = Palette.Amber, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

/** Kafelek z dowolną treścią w tym samym stylu (np. „Wszystkie aplikacje”). */
@Composable
fun ActionCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape = CardShape),
        colors = CardDefaults.colors(containerColor = Palette.Card, focusedContainerColor = Palette.Card),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Amber), shape = CardShape)),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
