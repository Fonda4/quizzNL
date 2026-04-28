    package com.example.quizznl

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quizznl.ui.theme.QuizzNLTheme
import org.json.JSONObject
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuizzNLTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QuizApp()
                }
            }
        }
    }
}

// --- MODÈLES DE DONNÉES ---
data class QuizTerm(val dutch: String, val french: String, val category: String)
data class QuizCategory(val name: String, val terms: List<QuizTerm>)
data class CurrentQuestion(
    val text: String,
    val correctText: String,
    val choices: List<String>,
    val category: String
)

enum class ScreenState { MENU, QUIZ }

@Composable
fun QuizApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(ScreenState.MENU) }
    var categories by remember { mutableStateOf<List<QuizCategory>>(emptyList()) }

    // Variables de gestion du quiz
    var quizMode by remember { mutableStateOf("Aléatoire") }
    var activeTerms by remember { mutableStateOf<List<QuizTerm>>(emptyList()) }
    var chapterTitle by remember { mutableStateOf("") }

    // Charger les données JSON au démarrage
    LaunchedEffect(Unit) {
        categories = chargerDonneesJson(context)
    }

    if (currentScreen == ScreenState.MENU) {
        MenuScreen(
            categories = categories,
            selectedMode = quizMode,
            onModeChange = { quizMode = it },
            onStartQuiz = { category ->
                chapterTitle = category.name
                activeTerms = category.terms
                currentScreen = ScreenState.QUIZ
            }
        )
    } else {
        QuizScreen(
            chapterTitle = chapterTitle,
            terms = activeTerms,
            quizMode = quizMode,
            onBackToMenu = { currentScreen = ScreenState.MENU }
        )
    }
}

// --- ÉCRAN : MENU PRINCIPAL ---
@Composable
fun MenuScreen(
    categories: List<QuizCategory>,
    selectedMode: String,
    onModeChange: (String) -> Unit,
    onStartQuiz: (QuizCategory) -> Unit
) {
    val modes = listOf("🇳🇱 ➡️ 🇫🇷", "🇫🇷 ➡️ 🇳🇱", "Aléatoire")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🎓 SÉLECTIONNEZ UN CHAPITRE",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 30.dp, bottom = 20.dp)
        )

        Text(text = "Mode de révision :", fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))

        // Équivalent du SegmentedButton
        Row(modifier = Modifier.padding(bottom = 20.dp)) {
            modes.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        if (categories.isEmpty()) {
            Text("Aucun chapitre trouvé. Avez-vous mis le fichier JSON dans 'assets' ?", color = Color.Red)
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(categories) { category ->
                    Button(
                        onClick = { onStartQuiz(category) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp, horizontal = 10.dp)
                            .height(50.dp)
                    ) {
                        Text(category.name)
                    }
                }
            }
        }
    }
}

// --- ÉCRAN : QUIZ ---
@Composable
fun QuizScreen(
    chapterTitle: String,
    terms: List<QuizTerm>,
    quizMode: String,
    onBackToMenu: () -> Unit
) {
    if (terms.size < 4) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Pas assez de termes dans ce chapitre pour un QCM (minimum 4).")
            Button(onClick = onBackToMenu, modifier = Modifier.padding(top = 16.dp)) { Text("Retour") }
        }
        return
    }

    var score by remember { mutableIntStateOf(0) }
    var totalQuestions by remember { mutableIntStateOf(0) }
    var currentQuestion by remember { mutableStateOf<CurrentQuestion?>(null) }

    // États pour le feedback et les couleurs des boutons
    var feedbackText by remember { mutableStateOf("") }
    var feedbackColor by remember { mutableStateOf(Color.White) }
    var answered by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    // Fonction pour générer une nouvelle question (équivalent de next_question)
    val generateQuestion = {
        answered = false
        feedbackText = ""
        selectedAnswer = null

        val qItem = terms.random()
        val direction = when (quizMode) {
            "🇳🇱 ➡️ 🇫🇷" -> "nl_fr"
            "🇫🇷 ➡️ 🇳🇱" -> "fr_nl"
            else -> listOf("nl_fr", "fr_nl").random()
        }

        val qText: String
        val correct: String
        val keyWrong: (QuizTerm) -> String

        if (direction == "nl_fr") {
            qText = "🇳🇱 ${qItem.dutch}  ➡️  🇫🇷 ?"
            correct = qItem.french
            keyWrong = { it.french }
        } else {
            qText = "🇫🇷 ${qItem.french}  ➡️  🇳🇱 ?"
            correct = qItem.dutch
            keyWrong = { it.dutch }
        }

        val autres = terms.filter { it != qItem }.map(keyWrong)
        val distracteurs = autres.shuffled().take(3)
        val choices = (distracteurs + correct).shuffled()

        currentQuestion = CurrentQuestion(qText, correct, choices, qItem.category)
    }

    // Générer la première question au lancement
    LaunchedEffect(Unit) { generateQuestion() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // En-tête : Titre et Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = chapterTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(text = "Score: $score/$totalQuestions", fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Zone Question
        currentQuestion?.let { question ->
            Text(text = "Thème : ${question.category}", color = Color.Gray)
            Text(
                text = question.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
            )

            // Zone Boutons (Grille 2x2 simulée avec des colonnes/lignes)
            Column(modifier = Modifier.fillMaxWidth()) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..1) {
                            val index = row * 2 + col
                            if (index < question.choices.size) {
                                val choice = question.choices[index]

                                // Logique des couleurs
                                val buttonColor = when {
                                    !answered -> MaterialTheme.colorScheme.primary
                                    choice == question.correctText -> Color(0xFF4CAF50) // Vert (Bonne réponse)
                                    choice == selectedAnswer && choice != question.correctText -> Color(0xFFF44336) // Rouge (Mauvaise réponse choisie)
                                    else -> Color.Gray
                                }

                                Button(
                                    onClick = {
                                        if (!answered) {
                                            selectedAnswer = choice
                                            answered = true
                                            totalQuestions++
                                            if (choice == question.correctText) {
                                                score++
                                                feedbackText = "✅ Correct !"
                                                feedbackColor = Color(0xFF4CAF50)
                                            } else {
                                                feedbackText = "❌ Incorrect. La bonne réponse était : ${question.correctText}"
                                                feedbackColor = Color(0xFFF44336)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                                    enabled = !answered || choice == question.correctText || choice == selectedAnswer,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp)
                                        .height(60.dp)
                                ) {
                                    Text(choice, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Feedback Label
        Text(
            text = feedbackText,
            color = feedbackColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Boutons de navigation (Bas de page)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBackToMenu) {
                Text("Retour au Menu")
            }
            Button(
                onClick = { generateQuestion() },
                enabled = answered
            ) {
                Text("Question Suivante")
            }
        }
    }
}

// --- LOGIQUE DE FICHIERS ---
fun chargerDonneesJson(context: Context): List<QuizCategory> {
    val categoriesList = mutableListOf<QuizCategory>()
    try {
        // Liste les fichiers dans le dossier assets/
        val files = context.assets.list("") ?: return emptyList()
        val jsonFile = files.firstOrNull { it.endsWith(".json") }

        if (jsonFile != null) {
            val inputStream = context.assets.open(jsonFile)
            val jsonString = InputStreamReader(inputStream).readText()
            val jsonObject = JSONObject(jsonString)

            val categoriesArray = jsonObject.optJSONArray("categories")
            if (categoriesArray != null) {
                for (i in 0 until categoriesArray.length()) {
                    val catObj = categoriesArray.getJSONObject(i)
                    val catName = catObj.optString("name", "Chapitre inconnu")
                    val termsArray = catObj.optJSONArray("terms")

                    val termsList = mutableListOf<QuizTerm>()
                    if (termsArray != null) {
                        for (j in 0 until termsArray.length()) {
                            val termObj = termsArray.getJSONObject(j)
                            if (termObj.has("dutch") && termObj.has("french")) {
                                val dutch = termObj.getString("dutch").substringBefore("[").trim()
                                val french = termObj.getString("french").substringBefore("[").trim()
                                termsList.add(QuizTerm(dutch, french, catName))
                            }
                        }
                    }
                    categoriesList.add(QuizCategory(catName, termsList))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return categoriesList
}