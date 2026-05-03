package com.example.blackout.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.blackout.data.entities.Category
import com.example.blackout.ui.theme.AutoAmber
import com.example.blackout.ui.theme.AutoAmberBg
import com.example.blackout.ui.theme.FinancialAmber
import com.example.blackout.ui.theme.FinancialAmberBg
import com.example.blackout.ui.theme.HomePink
import com.example.blackout.ui.theme.HomePinkBg
import com.example.blackout.ui.theme.IdGray
import com.example.blackout.ui.theme.IdGrayBg
import com.example.blackout.ui.theme.InsurancePurple
import com.example.blackout.ui.theme.InsurancePurpleBg
import com.example.blackout.ui.theme.MedicalBlue
import com.example.blackout.ui.theme.MedicalBlueBg
import com.example.blackout.ui.theme.NavyPrimary
import com.example.blackout.ui.theme.TaxGreen
import com.example.blackout.ui.theme.TaxGreenBg
import com.example.blackout.ui.theme.TealAccent
import com.example.blackout.ui.theme.TealWash

fun Category.pillColors(): Pair<Color, Color> = when (this) {
    Category.MEDICAL -> MedicalBlue to MedicalBlueBg
    Category.FINANCIAL -> FinancialAmber to FinancialAmberBg
    Category.TAX -> TaxGreen to TaxGreenBg
    Category.HOME -> HomePink to HomePinkBg
    Category.INSURANCE -> InsurancePurple to InsurancePurpleBg
    Category.ID_DOCUMENTS -> IdGray to IdGrayBg
    Category.IMMIGRATION -> TealAccent to TealWash
    Category.AUTO -> AutoAmber to AutoAmberBg
    Category.LEGAL -> NavyPrimary to MedicalBlueBg
    Category.OTHER -> IdGray to IdGrayBg
}

@Composable
fun CategoryPill(category: Category, modifier: Modifier = Modifier) {
    val (textColor, bgColor) = category.pillColors()
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = bgColor,
        modifier = modifier,
    ) {
        Text(
            text = "${category.emoji} ${category.label}",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
