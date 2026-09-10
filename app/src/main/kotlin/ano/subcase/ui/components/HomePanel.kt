package ano.subcase.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import ano.subcase.R
import ano.subcase.ui.theme.SubCaseTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val PanelShape = RoundedCornerShape(percent = 50)
private val IconSize = 44.dp

private val LightPanelFill = Color(0xFFF3F4F6)
private val DarkPanelFill = Color(0xFF404040)

private val LightItemIconBg = Color(0xFFFFFFFF)
private val DarkItemIconBg = Color(0xFF545454)

private val LightShadow = Color(0x0F000000)
private val DarkShadow = Color(0x33000000)

/**
 * 主界面底部悬浮面板，提供设置入口与本地服务启停。
 *
 * @param isServiceRunning 本地服务是否正在运行
 * @param onSettingsClick 点击设置按钮时的回调
 * @param onToggleService 点击启停按钮时的回调
 */
@Composable
fun HomePanel(
    isServiceRunning: Boolean,
    onSettingsClick: () -> Unit,
    onToggleService: () -> Unit,
    modifier: Modifier = Modifier,
    serviceEnabled: Boolean = true,
) {
    val darkTheme = isSystemInDarkTheme()
    val panelFill = if (darkTheme) DarkPanelFill else LightPanelFill
    val itemIconBg = if (darkTheme) DarkItemIconBg else LightItemIconBg
    val shadowColor = if (darkTheme) DarkShadow else LightShadow
    val actionDescription = stringResource(
        if (isServiceRunning) R.string.stop_service else R.string.start_service
    )
    val serviceStateDescription = stringResource(
        if (isServiceRunning) R.string.service_running else R.string.service_stopped
    )

    Column(
        modifier = modifier
            .dropShadow(
                shape = PanelShape,
                shadow = Shadow(
                    radius = 16.dp,
                    color = shadowColor,
                    offset = DpOffset(0.dp, 3.dp),
                ),
            )
            .clip(PanelShape)
            .background(panelFill)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.CenterVertically,
        ),
    ) {
        PanelItem(
            contentDescription = stringResource(R.string.open_settings),
            iconBackground = itemIconBg,
            shadowColor = shadowColor,
            onClick = onSettingsClick,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
        PanelItem(
            contentDescription = actionDescription,
            stateDescription = serviceStateDescription,
            iconBackground = itemIconBg,
            shadowColor = shadowColor,
            onClick = onToggleService,
            enabled = serviceEnabled,
        ) {
            Icon(
                painter = painterResource(
                    if (isServiceRunning) R.drawable.ic_stop else R.drawable.ic_play
                ),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 面板内的圆形图标按钮。
 *
 * @param contentDescription 无障碍内容描述，读屏软件会朗读该文案
 * @param iconBackground 圆形按钮背景色
 * @param shadowColor 圆形按钮阴影色
 * @param onClick 点击回调
 * @param stateDescription 可选的无障碍状态描述，例如服务运行/已停止
 * @param icon 按钮中央的图标内容
 */
@Composable
private fun PanelItem(
    contentDescription: String,
    iconBackground: Color,
    shadowColor: Color,
    onClick: () -> Unit,
    stateDescription: String? = null,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(IconSize)
            .dropShadow(
                shape = CircleShape,
                shadow = Shadow(
                    radius = 8.dp,
                    color = shadowColor,
                    offset = DpOffset(0.dp, 2.dp),
                ),
            )
            .background(iconBackground, CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription
                if (stateDescription != null) {
                    this.stateDescription = stateDescription
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** 主界面底部悬浮面板的预览。 */
@Preview(
    name = "Home panel",
    showBackground = true,
    backgroundColor = 0xFF4CAF50,
)
@Composable
private fun HomePanelPreview() {
    SubCaseTheme {
        Box(
            modifier = Modifier.padding(32.dp),
        ) {
            HomePanel(
                isServiceRunning = false,
                onSettingsClick = {},
                onToggleService = {},
            )
        }
    }
}
