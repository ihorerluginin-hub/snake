package com.biologigor.snake.game

import kotlin.random.Random

/** Направление движения и приращение клетки. */
enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    fun isOpposite(other: Dir): Boolean =
        this != other && dx == -other.dx && dy == -other.dy
}

/** Клетка поля. */
data class Pos(val x: Int, val y: Int)

/**
 * Чистая игровая логика: без Android-зависимостей, один шаг — один [tick].
 *
 * Змея хранится списком от головы к хвосту. Повороты складываются в очередь,
 * поэтому быстрые свайпы подряд не теряются и разворот на 180° невозможен.
 *
 * Уровни: каждые [POINTS_PER_LEVEL] очков — следующий уровень. Нечётные
 * уровни — поле с границей (стены убивают), чётные — сквозные стены и
 * препятствия, их раскладки чередуются от уровня к уровню. Кроме обычных
 * яблок есть красный бонус: +[BONUS_SCORE] очков, но живёт только
 * [BONUS_TTL_MS] миллисекунд.
 */
class Engine private constructor(
    val cols: Int,
    val rows: Int,
) {
    var snake: List<Pos> = emptyList()
        private set
    var dir: Dir = Dir.RIGHT
        private set
    var food: Pos = Pos(0, 0)
        private set

    /** Красный бонус (+5) или null, если его сейчас нет. */
    var bonus: Pos? = null
        private set

    /** Сколько миллисекунд осталось жить бонусу. */
    var bonusTtlMs: Long = 0
        private set

    var score: Int = 0
        private set
    var level: Int = 1
        private set

    /** Препятствия текущего уровня: врезался — погиб. */
    var obstacles: Set<Pos> = emptySet()
        private set

    var alive: Boolean = true
        private set
    var won: Boolean = false
        private set

    /** Задержка между шагами, мс. Уменьшается с каждым съеденным яблоком и уровнем. */
    var delayMs: Long = START_DELAY_MS
        private set

    /** Сквозные стены на чётных уровнях. */
    val wrap: Boolean get() = level % 2 == 0

    private val turnQueue = ArrayDeque<Dir>()

    fun turn(d: Dir) {
        val last = turnQueue.lastOrNull() ?: dir
        if (d.isOpposite(last) || d == last) return
        if (turnQueue.size < MAX_QUEUED_TURNS) turnQueue.addLast(d)
    }

    /** Один шаг игры. Меняет состояние на месте. */
    fun tick() {
        if (!alive) return
        val nd = if (turnQueue.isEmpty()) dir else turnQueue.removeFirst()
        dir = nd

        val head = snake.first()
        var nx = head.x + nd.dx
        var ny = head.y + nd.dy

        if (wrap) {
            nx = (nx + cols) % cols
            ny = (ny + rows) % rows
        } else if (nx !in 0 until cols || ny !in 0 until rows) {
            alive = false
            return
        }

        val newHead = Pos(nx, ny)
        val ate = newHead == food
        val ateBonus = bonus != null && newHead == bonus
        // Хвост уходит в этот же шаг, если не растём, — наступать на него можно
        val body = if (ate) snake else snake.dropLast(1)
        if (body.any { it == newHead } || newHead in obstacles) {
            alive = false
            return
        }

        snake = listOf(newHead) + snake
        if (ate) {
            score++
            food = spawnFood()
            if (!alive) return // поле заполнено — победа
            delayMs = (delayMs - SPEEDUP_MS).coerceAtLeast(MIN_DELAY_MS)
            maybeSpawnBonus()
        } else {
            snake = snake.dropLast(1)
        }
        if (ateBonus) {
            score += BONUS_SCORE
            bonus = null
            bonusTtlMs = 0
        }

        if (bonus != null) {
            bonusTtlMs -= delayMs
            if (bonusTtlMs <= 0) bonus = null
        }

        while (score >= level * POINTS_PER_LEVEL) levelUp()
    }

    private fun levelUp() {
        level++
        resetSnake()
        obstacles = buildObstacles()
        if (snake.any { it == food }) food = spawnFood()
        bonus = null
        bonusTtlMs = 0
        delayMs = (delayMs - LEVEL_SPEEDUP_MS).coerceAtLeast(MIN_DELAY_MS)
    }

    /** Сброс змейки к начальной длине и позиции в центре поля. */
    private fun resetSnake() {
        val cx = cols / 2
        val cy = rows / 2
        snake = (0 until START_LENGTH).map { Pos(cx - it, cy) }
        dir = Dir.RIGHT
        turnQueue.clear()
    }

    /**
     * Клетки раскладки текущего уровня, не занятые змейкой, едой, бонусом
     * и зоной прямо перед головой — чтобы препятствие не выросло под носом.
     */
    private fun buildObstacles(): Set<Pos> {
        val forbidden = snake.toHashSet()
        forbidden.add(food)
        bonus?.let { forbidden.add(it) }
        val h = snake.first()
        for (i in 0 until HEAD_CLEARANCE) {
            val nx = (h.x + dir.dx * i + cols) % cols
            val ny = (h.y + dir.dy * i + rows) % rows
            forbidden.add(Pos(nx, ny))
        }
        return obstaclePattern(level).filter { it !in forbidden }.toSet()
    }

    /** Раскладка препятствий по номеру уровня; с 3-й раскладки они циклятся. */
    private fun obstaclePattern(level: Int): List<Pos> {
        if (level < 2) return emptyList()
        val cx = cols / 2
        val cy = rows / 2
        return when ((level - 2) % 6) {
            // 2-й уровень: одна горизонтальная балка
            0 -> (cx - 4..cx + 3).map { Pos(it, cy) }
            // 3-й: две горизонтальные
            1 -> (cx - 6..cx + 5).map { Pos(it, cy - 6) } +
                    (cx - 6..cx + 5).map { Pos(it, cy + 6) }
            // 4-й: крест
            2 -> (cy - 6..cy + 5).map { Pos(cx, it) } +
                    (cx - 5..cx + 4).map { Pos(it, cy) }
            // 5-й: два столба
            3 -> (cy - 5..cy + 4).map { Pos(cx - 4, it) } +
                    (cy - 5..cy + 4).map { Pos(cx + 3, it) }
            // 6-й: четыре уголка-Г
            4 -> {
                val l = 5
                (2..2 + l - 1).map { Pos(it, 2) } + (2..2 + l - 1).map { Pos(2, it) } +
                        (cols - 2 - l + 1..cols - 3).map { Pos(it, 2) } +
                        (2..2 + l - 1).map { Pos(cols - 3, it) } +
                        (2..2 + l - 1).map { Pos(it, rows - 3) } +
                        (rows - 2 - l + 1..rows - 3).map { Pos(2, it) } +
                        (cols - 2 - l + 1..cols - 3).map { Pos(it, rows - 3) } +
                        (rows - 2 - l + 1..rows - 3).map { Pos(cols - 3, it) }
            }
            // 7-й и дальше: три ступеньки
            else -> (2..cx + 2).map { Pos(it, cy - 6) } +
                    (cx - 2..cols - 3).map { Pos(it, cy) } +
                    (2..cx + 2).map { Pos(it, cy + 6) }
        }.filter { it.x in 0 until cols && it.y in 0 until rows }
    }

    private fun maybeSpawnBonus() {
        if (bonus != null) return
        if (Random.nextInt(100) >= BONUS_CHANCE_PERCENT) return
        bonus = spawnFreeCell() ?: return
        bonusTtlMs = BONUS_TTL_MS
    }

    private fun spawnFood(): Pos {
        val p = spawnFreeCell()
        if (p == null) {
            won = true
            alive = false
            return snake.first()
        }
        return p
    }

    private fun spawnFreeCell(): Pos? {
        val occupied = snake.toHashSet()
        occupied.addAll(obstacles)
        occupied.add(food)
        val free = ArrayList<Pos>(cols * rows - occupied.size)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val p = Pos(x, y)
                if (p !in occupied) free.add(p)
            }
        }
        if (free.isEmpty()) return null
        return free[Random.nextInt(free.size)]
    }

    companion object {
        const val START_DELAY_MS = 220L
        const val MIN_DELAY_MS = 90L
        const val SPEEDUP_MS = 6L
        const val LEVEL_SPEEDUP_MS = 12L
        const val POINTS_PER_LEVEL = 20
        const val BONUS_SCORE = 5
        const val BONUS_TTL_MS = 10_000L
        private const val BONUS_CHANCE_PERCENT = 15
        private const val MAX_QUEUED_TURNS = 3
        private const val START_LENGTH = 3
        private const val HEAD_CLEARANCE = 6

        fun new(cols: Int, rows: Int): Engine {
            val e = Engine(cols, rows)
            val cx = cols / 2
            val cy = rows / 2
            e.snake = (0 until START_LENGTH).map { Pos(cx - it, cy) }
            e.dir = Dir.RIGHT
            e.food = e.spawnFood()
            return e
        }
    }
}
