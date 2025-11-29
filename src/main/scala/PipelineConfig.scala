
class PipelineConfig(
    val pipelineComplexMultiplication: Boolean,
    val pipelineButterflyFirstPart: Boolean,
    val pipelineButterflySecondPart: Boolean
)
object PipelineConfig {
    def apply(
        pipelineComplexMultiplication: Boolean,
        pipelineButterflyFirstPart: Boolean,
        pipelineButterflySecondPart: Boolean
    ): PipelineConfig = {
        new PipelineConfig(
            pipelineComplexMultiplication,
            pipelineButterflyFirstPart,
            pipelineButterflySecondPart
        )
    }
}
