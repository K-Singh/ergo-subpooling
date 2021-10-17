package app

object ShellHelper {
  sealed trait ShellState {
    override def toString(): String = {
      "$sp-shell"
    }
  }
  object ShellStates {
    case object mainState extends ShellState {
      override def toString(): String = super.toString()+".main"
    }
    case object createState extends ShellState{
      override def toString(): String = super.toString()+".create"
    }
    case object loadState extends ShellState{
      override def toString(): String = super.toString()+".load"
    }
    case object withdrawState extends ShellState{
      override def toString(): String = super.toString()+".load.withdraw"
    }
    case object walletsState extends ShellState{
      override def toString(): String = super.toString()+".wallets"
    }
    case object newState extends ShellState{
      override def toString(): String = super.toString()+".wallets.new"
    }
    case object joinState extends ShellState{
      override def toString(): String = super.toString()+".load.join"
    }
  }

  def shellInput(implicit shellState: ShellState): String = {
    print(shellState+": ")
    return scala.io.StdIn.readLine()
  }

  def shellDouble(implicit shellState: ShellState): Double = {
    print(shellState+": ")
    return scala.io.StdIn.readDouble()
  }
}
