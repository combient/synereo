package com.biosimilarity.lift.lib.kvdbJSON.Absyn; // Java Package generated by the BNF Converter.

public class KVDBReqHdr extends ReqHeader {
  public final URI uri_1, uri_2;
  public final UUID uuid_1, uuid_2;
  public final ReqJust reqjust_;

  public KVDBReqHdr(URI p1, URI p2, UUID p3, UUID p4, ReqJust p5) { uri_1 = p1; uri_2 = p2; uuid_1 = p3; uuid_2 = p4; reqjust_ = p5; }

  public <R,A> R accept(com.biosimilarity.lift.lib.kvdbJSON.Absyn.ReqHeader.Visitor<R,A> v, A arg) { return v.visit(this, arg); }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o instanceof com.biosimilarity.lift.lib.kvdbJSON.Absyn.KVDBReqHdr) {
      com.biosimilarity.lift.lib.kvdbJSON.Absyn.KVDBReqHdr x = (com.biosimilarity.lift.lib.kvdbJSON.Absyn.KVDBReqHdr)o;
      return this.uri_1.equals(x.uri_1) && this.uri_2.equals(x.uri_2) && this.uuid_1.equals(x.uuid_1) && this.uuid_2.equals(x.uuid_2) && this.reqjust_.equals(x.reqjust_);
    }
    return false;
  }

  public int hashCode() {
    return 37*(37*(37*(37*(this.uri_1.hashCode())+this.uri_2.hashCode())+this.uuid_1.hashCode())+this.uuid_2.hashCode())+this.reqjust_.hashCode();
  }


}
