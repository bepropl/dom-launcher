package pl.dom.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import pl.dom.launcher.LauncherApp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage

val WideCardWidth = 160.dp
val WideCardHeight = 90.dp
val PosterWidth = 100.dp
val PosterHeight = 150.dp
/** Duże plakaty filmów (zakładka Filmy, dwa rzędy). */
val BigPosterWidth = 124.dp
val BigPosterHeight = 186.dp

private val MediaShape = RoundedCornerShape(8.dp)

/** Ostatnio wybrana karta – po zamknięciu podglądu fokus wraca dokładnie na nią. */
object FocusMemory {
    var last: FocusRequester? = null
}

/** Spokojne, ciemne tło zastępcze, gdy nie ma obrazka (kolor zależy od tytułu). */
private fun placeholderColor(seed: String): Color {
    val palette = listOf(0xFF1B1430, 0xFF14202C, 0xFF2A1610, 0xFF102020, 0xFF241016, 0xFF1A1F12)
    return Color(palette[(seed.hashCode() and 0x7fffffff) % palette.size])
}

@Composable
private fun MediaCardFrame(
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val requester = remember { FocusRequester() }
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) FocusMemory.last = requester },
        shape = CardDefaults.shape(shape = MediaShape),
        colors = CardDefaults.colors(containerColor = Palette.Card, focusedContainerColor = Palette.Card),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Amber), shape = MediaShape)),
    ) { content() }
}

/** Logo serwisu z TMDB (np. „Netflix”) – do znaczka w rogu karty. */
@Composable
fun rememberServiceLogo(provider: String?): String? {
    if (provider.isNullOrBlank()) return null
    val app = LauncherApp.from(androidx.compose.ui.platform.LocalContext.current)
    val logos by app.media.providerLogos.collectAsState()
    return remember(provider, logos) { app.media.logoFor(provider) }
}

/** Znaczek z logo serwisu w prawym dolnym rogu karty. */
@Composable
private fun ServiceLogo(url: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(6.dp))
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp)),
    )
}

/** Karta 16:9 z obrazkiem na całą powierzchnię (nowe odcinki, Emby, „Dalej”). */
@Composable
fun WideMediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    badge: String? = null,
    badgeStrong: Boolean = true,
    progress: Float? = null,
    width: Dp = WideCardWidth,
    height: Dp = WideCardHeight,
    serviceLogo: String? = null,
) {
    MediaCardFrame(Modifier.size(width, height), onClick, onLongClick) {
        Box(Modifier.fillMaxSize().background(placeholderColor(title))) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f)),
                ),
            )
            if (badge != null) Badge(badge, badgeStrong, Modifier.align(Alignment.TopStart).padding(6.dp))
            if (serviceLogo != null) ServiceLogo(serviceLogo, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .padding(start = 8.dp, end = if (serviceLogo != null) 38.dp else 8.dp, top = 6.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 8.sp, color = Palette.Soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (progress != null) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Palette.Amber))
                    }
                }
            }
        }
    }
}

/** Pionowy plakat (Filmy, Seriale, lista do obejrzenia). */
@Composable
fun PosterCard(
    title: String,
    label: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    width: Dp = PosterWidth,
    height: Dp = PosterHeight,
    labelStrong: Boolean = false,
    serviceLogo: String? = null,
) {
    MediaCardFrame(Modifier.size(width, height), onClick, onLongClick) {
        Box(Modifier.fillMaxSize().background(placeholderColor(title))) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp), maxLines = 3,
                )
            }
            if (label != null) Badge(label, strong = labelStrong, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            if (serviceLogo != null) ServiceLogo(serviceLogo, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

/** Mały poziomy kafelek z miniaturą (np. „Czekacie na nowe odcinki”). */
@Composable
fun SmallMediaCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    MediaCardFrame(Modifier.size(200.dp, 46.dp), onClick, onLongClick) {
        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)).background(placeholderColor(title))) {
                if (imageUrl != null) AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 8.sp, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Badge(text: String, strong: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(if (strong) Palette.Amber else Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        fontSize = 7.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (strong) Palette.OnAmber else Palette.Amber,
        maxLines = 1,
    )
}

/** Nagłówek sekcji z opcjonalnym dopiskiem. */
@Composable
fun SectionTitle(title: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 11.sp, color = Palette.Soft)
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(hint, fontSize = 8.sp, color = Palette.Muted)
        }
    }
}

/** Rząd kart z tytułem. */
@Composable
fun MediaRow(title: String, hint: String? = null, content: LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(start = EdgePadding + 4.dp)) { SectionTitle(title, hint) }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = EdgePadding + 4.dp, vertical = 6.dp),
            content = content,
        )
    }
}

/** Kilka rzędów plakatów przewijanych w bok (np. seriale w trzech rzędach, filmy w dwóch). */
@Composable
fun MediaGrid(
    title: String,
    hint: String? = null,
    rows: Int,
    cellHeight: Dp,
    content: LazyGridScope.() -> Unit,
) {
    val spacing = 12.dp
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.padding(start = EdgePadding + 4.dp)) { SectionTitle(title, hint) }
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier.fillMaxWidth().height(cellHeight * rows + spacing * (rows - 1) + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = PaddingValues(horizontal = EdgePadding + 4.dp, vertical = 8.dp),
            content = content,
        )
    }
}

/** Informacja w miejscu pustego rzędu. */
@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        modifier = Modifier
            .padding(horizontal = EdgePadding)
            .background(Palette.Card.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        fontSize = 10.sp,
        color = Palette.Muted,
    )
}
