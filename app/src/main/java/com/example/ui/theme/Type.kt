package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.example.R

val GothamRounded = FontFamily(
    Font(resId = R.font.gothamrnd_light, weight = FontWeight.Light),
    Font(resId = R.font.gothamrnd_lightitalic, weight = FontWeight.Light, style = FontStyle.Italic),
    Font(resId = R.font.gothamrnd_book, weight = FontWeight.Normal),
    Font(resId = R.font.gothamrnd_bookitalic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resId = R.font.gothamrnd_medium, weight = FontWeight.Medium),
    Font(resId = R.font.gothamrnd_mediumitalic, weight = FontWeight.Medium, style = FontStyle.Italic),
    Font(resId = R.font.gothamrnd_bold, weight = FontWeight.Bold),
    Font(resId = R.font.gothamrnd_bolditalic, weight = FontWeight.Bold, style = FontStyle.Italic)
)

// Set of Material typography styles config (restored to default for perfect system legibility in the rest of the app)
val Typography = Typography()
