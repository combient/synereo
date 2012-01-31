package com.biosimilarity.lift.lib.kvdbJSON.Absyn; // Java Package generated by the BNF Converter.

public abstract class Substitution implements java.io.Serializable {
  public abstract <R,A> R accept(Substitution.Visitor<R,A> v, A arg);
  public interface Visitor <R,A> {
    public R visit(com.biosimilarity.lift.lib.kvdbJSON.Absyn.KVDBSubst p, A arg);

  }

}