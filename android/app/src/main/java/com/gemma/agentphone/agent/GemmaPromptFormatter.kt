package com.gemma.agentphone.agent

/**
 * Wraps a plain system+user prompt in the Gemma 4 instruct chat template.
 *
 * Gemma 4 instruction-tuned models are trained with the following turn markers:
 *   <start_of_turn>user\n{content}<end_of_turn>\n<start_of_turn>model\n
 *
 * Sending raw text without these tokens causes the model to treat the entire
 * prompt as a continuation of a pre-training document rather than an
 * instruction, which results in verbose filler, repeated instructions, or
 * malformed JSON outputs in the agent context.
 *
 * All prompt paths in the accessibility agent loop should go through this formatter.
 */
object GemmaPromptFormatter {

    private const val START_TURN = "<start_of_turn>"
    private const val END_TURN = "<end_of_turn>"
    private const val USER_ROLE = "user"
    private const val MODEL_ROLE = "model"

    /**
     * Wraps [content] in the Gemma 4 instruct user turn and opens the model turn.
     * The returned string ends just after `<start_of_turn>model\n` so the model
     * begins generating its reply immediately without any preamble.
     *
     * @param content The combined system instructions + user task text.
     * @return Formatted string ready to be fed directly to LlmInferenceSession.
     */
    fun wrap(content: String): String = buildString {
        append(START_TURN)
        append(USER_ROLE)
        append('\n')
        append(content.trim())
        append(END_TURN)
        append('\n')
        append(START_TURN)
        append(MODEL_ROLE)
        append('\n')
    }

    /**
     * Strips any Gemma turn markers from a raw model output so that downstream
     * parsers only see the payload text (typically a JSON object).
     *
     * @param rawOutput The raw string returned by the LLM.
     * @return Cleaned output with turn markers removed and surrounding whitespace trimmed.
     */
    fun stripMarkers(rawOutput: String): String {
        return rawOutput
            .replace(START_TURN, "")
            .replace(END_TURN, "")
            .replace(USER_ROLE, "")
            .replace(MODEL_ROLE, "")
            .trim()
    }
}
