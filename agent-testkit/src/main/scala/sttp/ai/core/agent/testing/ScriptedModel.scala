package sttp.ai.core.agent.testing

import sttp.ai.core.model.{AIModel, Capability}

/** Model stand-in for scripted agents. Claims every capability so builder evidence never blocks a test. */
case object ScriptedModel
    extends AIModel
    with Capability.Vision
    with Capability.ToolCalling
    with Capability.StructuredOutput
    with Capability.Reasoning {
  val value: String = "scripted-model"
}
