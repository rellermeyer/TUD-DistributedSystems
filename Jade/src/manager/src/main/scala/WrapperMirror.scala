import ComponentState.ComponentState
import JadeManager.BindingConfig
import WrapperId.WrapperId

/**
  * Mirror of a wrapper, holding its state and lifecycle methods.
  */
class WrapperMirror(val id: WrapperId) extends Serializable {
    var binding: BindingConfig = _
    var state: ComponentState = ComponentState.Running
}
