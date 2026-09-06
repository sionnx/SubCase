package ano.subcase.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ano.subcase.ui.theme.SubCaseTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 设置分组的视觉参数，沿用现有设置页样式。 */
object SectionDefaults {
    val CornerRadius = CardDefaults.CornerRadius
    val InsideMargin = CardDefaults.InsideMargin
    val RowHeight = 50.dp
    val HorizontalPadding = 10.dp
    val DividerStartPadding = 10.dp
    val DividerThickness = 0.5.dp
    val DividerColor = Color.LightGray
    val HeaderSpacing = 3.dp
    val FooterSpacing = 5.dp
    val CaptionFontSize = 14.sp
    val FooterColor = Color(0xFF909399)
    val WarningColor = Color(0xFFfaad14)
    val ContainerColor: Color @Composable get() = MiuixTheme.colorScheme.surface
    val HeaderColor: Color @Composable get() = MiuixTheme.colorScheme.onBackground
}

class SectionScope internal constructor() {
    internal val items = mutableListOf<@Composable RowScope.() -> Unit>()

    fun item(content: @Composable RowScope.() -> Unit) {
        items.add(content)
    }
}

/** 标题、圆角行组和说明；相邻项目之间自动绘制分割线。 */
@Composable
fun Section(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    footerColor: Color = SectionDefaults.FooterColor,
    content: SectionScope.() -> Unit,
) {
    val scope = SectionScope().apply(content)
    Column(modifier = modifier.fillMaxWidth()) {
        if (!header.isNullOrBlank()) {
            Text(
                text = header,
                modifier = Modifier.padding(start = SectionDefaults.HorizontalPadding),
                color = SectionDefaults.HeaderColor,
                fontSize = SectionDefaults.CaptionFontSize,
            )
            Spacer(Modifier.height(SectionDefaults.HeaderSpacing))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = SectionDefaults.CornerRadius,
            insideMargin = SectionDefaults.InsideMargin,
            colors = CardDefaults.defaultColors(color = SectionDefaults.ContainerColor),
        ) {
            scope.items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = SectionDefaults.DividerStartPadding),
                        color = SectionDefaults.DividerColor,
                        thickness = SectionDefaults.DividerThickness,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SectionDefaults.RowHeight)
                        .padding(horizontal = SectionDefaults.HorizontalPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    content = item,
                )
            }
        }
        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = SectionDefaults.HorizontalPadding,
                        top = SectionDefaults.FooterSpacing,
                    ),
                color = footerColor,
                fontSize = SectionDefaults.CaptionFontSize,
            )
        }
    }
}

@Preview(name = "Section · 浅色", showBackground = true, backgroundColor = 0xFFF4F4F4)
@Preview(
    name = "Section · 深色",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SectionPreview() {
    SubCaseTheme {
        var showWarning by remember { mutableStateOf(true) }
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section {
                item { Text("单行分组") }
            }
            Section(header = "后端", footer = "你可以点击backend地址,来快速复制") {
                item {
                    Text("地址")
                    Text("http://127.0.0.1:8081")
                }
                item {
                    Text("版本")
                    Text("2.38.2")
                }
            }
            Section(
                footer = if (showWarning) "当前不是WIFI环境,无法获取准确的局域网IP!" else null,
                footerColor = SectionDefaults.WarningColor,
            ) {
                item {
                    Text("条件说明")
                    Switch(checked = showWarning, onCheckedChange = { showWarning = it })
                }
            }
            Section(header = " ", footer = "") {
                item { Text("空白标题与说明收起") }
            }
        }
    }
}
