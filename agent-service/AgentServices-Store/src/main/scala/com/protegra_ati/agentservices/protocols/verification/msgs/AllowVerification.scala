// -*- mode: Scala;-*- 
// Filename:    AllowVerification.scala 
// Authors:     lgm                                                    
// Creation:    Mon Jan 27 09:31:23 2014 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.protegra_ati.agentservices.protocols.msgs

import com.biosimilarity.evaluator.distribution.PortableAgentCnxn
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.protegra_ati.agentservices.store.extensions.StringExtensions._

case class AllowVerification(
  override val sessionId : String,
  override val correlationId : String,
  relyingParty : PortableAgentCnxn,
  claim : CnxnCtxtLabel[String,String,String]
) extends VerificationMessage( sessionId, correlationId ) {
  override def toLabel : CnxnCtxtLabel[String,String,String] = {
    AllowVerification.toLabel( sessionId )
  }
}

object AllowVerification {
  def toLabel(): CnxnCtxtLabel[String, String, String] = {
    "protocolMessage(allowVerification(sessionId(_)))".toLabel
  }

  def toLabel(sessionId: String): CnxnCtxtLabel[String, String, String] = {
    s"""protocolMessage(allowVerification(sessionId(\"$sessionId\")))""".toLabel
  }
}
