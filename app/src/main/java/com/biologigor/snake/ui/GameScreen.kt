package com.biologigor.snake.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.biologigor.snake.data.Prefs
import com.biologigor.snake.data.Settings
import com.biologigor.snake.fx.Beeper
import com.biologigor.snake.fx.Haptics
import com.biologigor.snake.game.Dir
import com.biologigor.snake.game.Difficulty
import com.biologigor.snake.game.Engine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Экранные состояния игры. */
sealed interface Screen {
    data object Menu : Screen
    data object Running : Screen
    data object Paused : Screen
    data object GameOver : Screen
}

private const val COLS = 20
private const val ROWS = 24

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var settings by remember { mutableStateOf(prefs.load()) }
    val beeper = remember { Beeper() }
    val haptics = remember { Haptics(context) }
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    var engine by remember { mutableStateOf<Engine?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    var stepT by remember { mutableFloatStateOf(0f) }
    var newBest by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var levelBanner by remember { mutableStateOf<Int?>(null) }

    fun startGame() {
        engine = Engine.new(COLS, ROWS, settings.difficulty)
        newBest = false
        levelBanner = null
        screen = Screen.Running
    }

    fun finishGame(e: Engine) {
        if (e.score > settings.best(e.difficulty)) {
            settings = settings.withBest(e.difficulty, e.score)
            prefs.save(settings)
            newBest = true
        }
        screen = Screen.GameOver
    }

    // Во время игры экран не гаснет
    val view = LocalView.current
    DisposableEffect(screen) {
        view.keepScreenOn = screen == Screen.Running
        onDispose { view.keepScreenOn = false }
    }

    // Уход в фон во время игры — автоматическая пауза
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && screen == Screen.Running) {
                screen = Screen.Paused
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { beeper.release() }
    }

    // Баннер уровня гаснет сам
    LaunchedEffect(levelBanner) {
        if (levelBanner != null) {
            delay(1100)
            levelBanner = null
        }
    }

    // Игровой цикл: один проход на кадр, шаги движка — по его таймеру
    LaunchedEffect(screen) {
        if (screen != Screen.Running) return@LaunchedEffect
        // После паузы стартуем с текущего места, без рывка назад
        engine?.syncPrevSnake()
        var lastNs = withFrameNanos { it }
        var acc = 0L
        while (screen == Screen.Running) {
            val now = withFrameNanos { it }
            acc += now - lastNs
            lastNs = now
            val e = engine ?: break
            var justAte = false
            var justBonus = false
            var justLevelUp = false
            var justDied = false
            while (acc >= e.delayMs * 1_000_000 && e.alive) {
                acc -= e.delayMs * 1_000_000
                val scoreBefore = e.score
                val levelBefore = e.level
                e.tick()
                val gained = e.score - scoreBefore
                if (gained >= Engine.BONUS_SCORE) justBonus = true
                else if (gained > 0) justAte = true
                if (e.level > levelBefore) justLevelUp = true
                if (!e.alive) justDied = true
            }
            // Доля пройденного шага: 0 — только что тикнули, 1 — почти следующий
            stepT = if (e.alive) (acc / (e.delayMs * 1_000_000f)).coerceIn(0f, 1f) else 1f
            frame = now
            if (justAte) {
                if (settings.sound) beeper.eat()
                if (settings.vibration) haptics.light()
            }
            if (justBonus) {
                if (settings.sound) beeper.bonus()
                if (settings.vibration) haptics.medium()
            }
            if (justLevelUp) {
                levelBanner = e.level
                if (settings.sound) beeper.levelUp()
                if (settings.vibration) haptics.medium()
            }
            if (justDied) {
                if (settings.sound) beeper.die()
                if (settings.vibration) haptics.heavy()
                finishGame(e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Верхняя панель
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Счёт: ${engine?.score ?: 0}",
                    color = TextMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Рекорд: ${settings.best(engine?.difficulty ?: settings.difficulty)}",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Ур. ${engine?.level ?: 1}",
                    color = Accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "ещё ${(engine?.level ?: 1) * Engine.POINTS_PER_LEVEL - (engine?.score ?: 0)}",
                    color = TextDim,
                    fontSize = 12.sp,
                )
            }
            if (screen == Screen.Running) {
                Spacer(Modifier.size(12.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x22FFFFFF), CircleShape)
                        .clickable { screen = Screen.Paused },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⏸", color = TextMain, fontSize = 18.sp)
                }
            }
        }

        // Поле
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
        ) {
            BoardCanvas(
                engine = engine,
                frame = frame,
                stepT = stepT,
                modifier = Modifier.fillMaxSize(),
            )

            when (screen) {
                Screen.Menu -> if (showSettings) {
                    SettingsPanel(
                        settings = settings,
                        onChange = { settings = it; prefs.save(it) },
                        onClose = { showSettings = false },
                    )
                } else {
                    MenuPanel(
                        difficulty = settings.difficulty,
                        best = settings.best(settings.difficulty),
                        onPlay = ::startGame,
                        onSettings = { showSettings = true },
                    )
                }

                Screen.Paused -> PausePanel(
                    onResume = { screen = Screen.Running },
                    onRestart = ::startGame,
                    onMenu = { screen = Screen.Menu },
                )

                Screen.GameOver -> GameOverPanel(
                    won = engine?.won == true,
                    score = engine?.score ?: 0,
                    newBest = newBest,
                    onRetry = ::startGame,
                    onMenu = { screen = Screen.Menu },
                )

                Screen.Running -> Unit
            }

            // Баннер при переходе на новый уровень
            levelBanner?.let { lvl ->
                Surface(
                    color = Accent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 14.dp),
                ) {
                    Text(
                        text = "УРОВЕНЬ $lvl",
                        color = Color(0xFF0B1A0F),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Большая квадратная кнопка управления — как на старых Нокиях
        if (screen == Screen.Running) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NokiaPad(onDir = { d -> engine?.turn(d) })
            }
        }
    }
}

// ---------------------------------------------------------------- отрисовка поля

@Composable
private fun BoardCanvas(
    engine: Engine?,
    frame: Long,
    stepT: Float,
    modifier: Modifier = Modifier,
) {
    // Управление только кнопкой, жестов на поле нет
    Canvas(modifier = modifier) {
        // Чтение frame в draw и есть подписка на перерисовку: меняем его каждый кадр
        drawBoard(engine, frame, stepT)
    }
}

private fun DrawScope.drawBoard(engine: Engine?, frame: Long, stepT: Float) {
    val cell = min(size.width / COLS, size.height / ROWS)
    val boardW = cell * COLS
    val boardH = cell * ROWS
    val ox = (size.width - boardW) / 2f
    val oy = (size.height - boardH) / 2f
    val inset = cell * 0.06f
    val corner = CornerRadius(cell * 0.28f, cell * 0.28f)

    // Шахматный фон
    for (y in 0 until ROWS) {
        for (x in 0 until COLS) {
            drawRect(
                color = if ((x + y) % 2 == 0) BoardA else BoardB,
                topLeft = Offset(ox + x * cell, oy + y * cell),
                size = Size(cell, cell),
            )
        }
    }
    // Рамка
    drawRect(
        color = BoardBorder,
        topLeft = Offset(ox, oy),
        size = Size(boardW, boardH),
        style = Stroke(width = 2.dp.toPx()),
    )

    if (engine == null) return

    // Препятствия уровня
    for (p in engine.obstacles) {
        drawRoundRect(
            color = Obstacle,
            topLeft = Offset(ox + p.x * cell + inset * 0.5f, oy + p.y * cell + inset * 0.5f),
            size = Size(cell - inset, cell - inset),
            cornerRadius = corner,
        )
    }

    // Яблоко с лёгкой пульсацией
    val pulse = 0.85f + 0.15f * (0.5f + 0.5f * sin(frame / 150_000_000.0)).toFloat()
    val fx = ox + (engine.food.x + 0.5f) * cell
    val fy = oy + (engine.food.y + 0.5f) * cell
    drawCircle(Apple, radius = cell * 0.30f * pulse, center = Offset(fx, fy))
    drawCircle(Leaf, radius = cell * 0.10f, center = Offset(fx + cell * 0.16f, fy - cell * 0.30f))

    // Красный бонус: +5, вокруг дуга-таймер, в конце жизни мигает
    engine.bonus?.let { b ->
        val bx = ox + (b.x + 0.5f) * cell
        val by = oy + (b.y + 0.5f) * cell
        val blink = engine.bonusTtlMs < 3000 && (frame / 130_000_000L) % 2L == 0L
        if (!blink) {
            drawCircle(BonusRed, radius = cell * 0.40f * pulse, center = Offset(bx, by))
            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = cell * 0.09f,
                center = Offset(bx - cell * 0.12f, by - cell * 0.12f),
            )
        }
        drawArc(
            color = Color.White,
            startAngle = -90f,
            sweepAngle = (360f * engine.bonusTtlMs.toFloat() / Engine.BONUS_TTL_MS)
                .coerceIn(0f, 360f),
            useCenter = false,
            topLeft = Offset(bx - cell * 0.48f, by - cell * 0.48f),
            size = Size(cell * 0.96f, cell * 0.96f),
            style = Stroke(width = 2.dp.toPx()),
        )
    }

    // Змея и глаза — срезаются по рамке поля: при сквозных стенах сегмент
    // уезжает за один край и доезжает с другого, там рисуется его копия
    clipRect(ox, oy, ox + boardW, oy + boardH) {
        // Змея: от хвоста к голове, голова рисуется поверх.
        // Между тиками сегменты скользят от прошлой клетки к новой.
        val snake = engine.snake
        val prevSnake = engine.prevSnake
        for (i in snake.indices.reversed()) {
            val cur = snake[i]
            val prev = prevSnake.getOrNull(i) ?: cur
            val x = lerpCell(prev.x, cur.x, stepT, COLS)
            val y = lerpCell(prev.y, cur.y, stepT, ROWS)
            val k = if (snake.size > 1) i.toFloat() / (snake.size - 1) else 0f
            val color = lerp(SnakeHead, SnakeTail, k)
            for ((cx, cy) in cellCopies(x, y)) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(ox + cx * cell + inset, oy + cy * cell + inset),
                    size = Size(cell - 2 * inset, cell - 2 * inset),
                    cornerRadius = corner,
                )
            }
        }

        // Глаза на голове, смотрят по направлению движения
        val head = snake.first()
        val headPrev = prevSnake.getOrNull(0) ?: head
        val hx = lerpCell(headPrev.x, head.x, stepT, COLS)
        val hy = lerpCell(headPrev.y, head.y, stepT, ROWS)
        val off = cell * 0.17f
        val eyeR = cell * 0.09f
        val pupilR = eyeR * 0.55f
        val fwdX = engine.dir.dx * off
        val fwdY = engine.dir.dy * off
        val perpX = if (engine.dir.dx != 0) 0f else -off
        val perpY = if (engine.dir.dy != 0) 0f else -off
        for ((ccx, ccy) in cellCopies(hx, hy)) {
            val hcx = ox + (ccx + 0.5f) * cell
            val hcy = oy + (ccy + 0.5f) * cell
            for (s in intArrayOf(1, -1)) {
                val ex = hcx + fwdX + perpX * s
                val ey = hcy + fwdY + perpY * s
                drawCircle(Color.White, eyeR, Offset(ex, ey))
                drawCircle(
                    Color(0xFF0E141B),
                    pupilR,
                    Offset(ex + engine.dir.dx * eyeR * 0.4f, ey + engine.dir.dy * eyeR * 0.4f),
                )
            }
        }
    }
}

/**
 * Плавная координата клетки между тиками. Переход через край поля
 * (сквозные стены) идёт по короткой дуге — за соседний край, а не через всё поле.
 */
private fun lerpCell(prev: Int, cur: Int, stepT: Float, n: Int): Float {
    var d = cur - prev
    if (d > 1 || d < -1) {
        if (d > n / 2) d -= n else if (d < -n / 2) d += n
    }
    return prev + d * stepT
}

/** Клетка и её копия с противоположного края, если сегмент пересекает границу поля. */
private fun cellCopies(x: Float, y: Float): List<Pair<Float, Float>> = buildList {
    add(x to y)
    if (x < 0) add((x + COLS) to y)
    if (x > COLS - 1) add((x - COLS) to y)
    if (y < 0) add(x to (y + ROWS))
    if (y > ROWS - 1) add(x to (y - ROWS))
}

// ---------------------------------------------------------------- панели-оверлеи

@Composable
private fun OverlayPanel(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            color = Color(0xF0101A26),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun MenuPanel(
    difficulty: Difficulty,
    best: Int,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    OverlayPanel {
        Text("ЗМЕЙКА", color = Accent, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.size(8.dp))
        Text(
            "Большая кнопка внизу: жми её край, куда нужно ползти",
            color = TextDim,
            fontSize = 13.sp,
        )
        Spacer(Modifier.size(16.dp))
        Text("Сложность: ${difficulty.label}", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.size(4.dp))
        Text("Рекорд: $best", color = TextMain, fontSize = 16.sp)
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B1A0F)),
        ) { Text("Играть", fontSize = 18.sp) }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(onClick = onSettings) { Text("Настройки") }
    }
}

@Composable
private fun PausePanel(onResume: () -> Unit, onRestart: () -> Unit, onMenu: () -> Unit) {
    OverlayPanel {
        Text("Пауза", color = TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = onResume,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B1A0F)),
        ) { Text("Продолжить") }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(onClick = onRestart) { Text("Заново") }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(onClick = onMenu) { Text("В меню") }
    }
}

@Composable
private fun GameOverPanel(
    won: Boolean,
    score: Int,
    newBest: Boolean,
    onRetry: () -> Unit,
    onMenu: () -> Unit,
) {
    OverlayPanel {
        Text(
            text = if (won) "Победа!" else "Игра окончена",
            color = if (won) Accent else Color(0xFFFF7B7B),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.size(12.dp))
        Text("Счёт: $score", color = TextMain, fontSize = 20.sp)
        if (newBest) {
            Spacer(Modifier.size(6.dp))
            Text("Новый рекорд!", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(20.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF0B1A0F)),
        ) { Text("Ещё раз") }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(onClick = onMenu) { Text("В меню") }
    }
}

@Composable
private fun SettingsPanel(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onClose: () -> Unit,
) {
    OverlayPanel {
        Text("Настройки", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(14.dp))
        SettingRow("Звук", settings.sound) { onChange(settings.copy(sound = it)) }
        SettingRow("Вибрация", settings.vibration) { onChange(settings.copy(vibration = it)) }
        Spacer(Modifier.size(14.dp))
        Text("Сложность", color = TextMain, fontSize = 16.sp)
        Spacer(Modifier.size(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Difficulty.values().forEach { d ->
                val selected = d == settings.difficulty
                if (selected) {
                    Button(
                        onClick = { onChange(settings.copy(difficulty = d)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text(d.label, fontSize = 13.sp) }
                } else {
                    OutlinedButton(
                        onClick = { onChange(settings.copy(difficulty = d)) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) { Text(d.label, color = TextDim, fontSize = 13.sp) }
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            "Влияет на разгон скорости, рекорд у каждой свой",
            color = TextDim,
            fontSize = 12.sp,
        )
        Spacer(Modifier.size(14.dp))
        Button(onClick = onClose) { Text("Готово") }
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(label, color = TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------- большая кнопка управления

/**
 * Одна большая квадратная кнопка с ободком, как на телефонах с кнопками.
 * Нажатие по верхнему краю ведёт наверх, по нижнему — вниз и так далее;
 * центр — мёртвая зона.
 */
@Composable
private fun NokiaPad(onDir: (Dir) -> Unit) {
    var pressed by remember { mutableStateOf<Dir?>(null) }
    Box(
        modifier = Modifier
            .padding(bottom = 12.dp)
            .size(188.dp)
            .background(PadFace, RoundedCornerShape(26.dp))
            .border(3.dp, PadRim, RoundedCornerShape(26.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val dx = down.position.x - size.width / 2f
                    val dy = down.position.y - size.height / 2f
                    if (hypot(dx, dy) > minOf(size.width, size.height) * 0.18f) {
                        val d = if (abs(dx) > abs(dy)) {
                            if (dx > 0) Dir.RIGHT else Dir.LEFT
                        } else {
                            if (dy > 0) Dir.DOWN else Dir.UP
                        }
                        pressed = d
                        onDir(d)
                    }
                    // Держим подсветку зоны до отпускания пальца
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                    }
                    pressed = null
                }
            },
    ) {
        pressed?.let { d ->
            val (align, shape) = when (d) {
                Dir.UP -> Alignment.TopCenter to Modifier.fillMaxWidth(0.6f).height(44.dp)
                Dir.DOWN -> Alignment.BottomCenter to Modifier.fillMaxWidth(0.6f).height(44.dp)
                Dir.LEFT -> Alignment.CenterStart to Modifier.width(44.dp).fillMaxHeight(0.6f)
                Dir.RIGHT -> Alignment.CenterEnd to Modifier.width(44.dp).fillMaxHeight(0.6f)
            }
            Box(
                modifier = Modifier
                    .align(align)
                    .then(shape)
                    .background(Accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
            )
        }
        PadArrow("▲", Modifier.align(Alignment.TopCenter).padding(top = 10.dp), pressed == Dir.UP)
        PadArrow("▼", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), pressed == Dir.DOWN)
        PadArrow("◀", Modifier.align(Alignment.CenterStart).padding(start = 14.dp), pressed == Dir.LEFT)
        PadArrow("▶", Modifier.align(Alignment.CenterEnd).padding(end = 14.dp), pressed == Dir.RIGHT)
    }
}

@Composable
private fun PadArrow(label: String, modifier: Modifier, active: Boolean) {
    Text(
        label,
        color = if (active) Accent else TextDim,
        fontSize = 22.sp,
        fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
        modifier = modifier,
    )
}
