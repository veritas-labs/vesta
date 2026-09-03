package uk.veritaslabs.vesta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import uk.veritaslabs.vesta.ui.theme.VestaTheme
import vesta.shared.generated.resources.Res
import vesta.shared.generated.resources.compose_multiplatform

/**
 * The profile-picker avatar content, for use as [AvatarButton]'s `avatar`
 * slot: renders [avatar] if given, otherwise falls back to [name]'s initial
 * — see docs/design/profile-switching.md's Profile Card.
 */
@Composable
fun ProfileAvatar(name: String, size: Dp, avatar: DrawableResource? = null) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar != null) {
            Image(
                painter = painterResource(avatar),
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initialsFor(name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

internal fun initialsFor(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

@Composable
private fun ProfilePickerPhoneContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        AvatarButton(
            label = "Tom",
            avatar = { size -> ProfileAvatar(name = "Tom", size = size) },
        )
        Spacer(Modifier.height(12.dp))
        AvatarButton(
            label = "Arthur",
            avatar = { size ->
                ProfileAvatar(name = "Arthur", size = size, avatar = Res.drawable.compose_multiplatform)
            },
        )
        Spacer(Modifier.height(12.dp))
        AvatarButton(
            label = "Mei",
            avatar = { size -> ProfileAvatar(name = "Mei", size = size) },
        )
    }
}

@Composable
private fun ProfilePickerTabletContent() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AvatarButton(
            label = "Tom",
            modifier = Modifier.weight(1f),
            orientation = AvatarButtonOrientation.Vertical,
            avatar = { size -> ProfileAvatar(name = "Tom", size = size) },
        )
        AvatarButton(
            label = "Arthur",
            modifier = Modifier.weight(1f),
            orientation = AvatarButtonOrientation.Vertical,
            avatar = { size ->
                ProfileAvatar(name = "Arthur", size = size, avatar = Res.drawable.compose_multiplatform)
            },
        )
        AvatarButton(
            label = "Mei",
            modifier = Modifier.weight(1f),
            orientation = AvatarButtonOrientation.Vertical,
            avatar = { size -> ProfileAvatar(name = "Mei", size = size) },
        )
    }
}

@Preview(name = "Light", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfilePickerPhoneLightPreview() {
    VestaTheme(darkTheme = false) { ProfilePickerPhoneContent() }
}

@Preview(name = "Dark", group = "Phone", device = Devices.PHONE)
@Composable
private fun ProfilePickerPhoneDarkPreview() {
    VestaTheme(darkTheme = true) { ProfilePickerPhoneContent() }
}

@Preview(name = "Light", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfilePickerTabletLightPreview() {
    VestaTheme(darkTheme = false) { ProfilePickerTabletContent() }
}

@Preview(name = "Dark", group = "Tablet", device = Devices.TABLET)
@Composable
private fun ProfilePickerTabletDarkPreview() {
    VestaTheme(darkTheme = true) { ProfilePickerTabletContent() }
}
