// -*- mode: Scala;-*- 
// Filename:    Driver.scala 
// Authors:     lgm                                                    
// Creation:    Thu Feb 13 16:40:06 2014 
// Copyright:   Not supplied 
// Description: 
// ------------------------------------------------------------------------

package com.protegra_ati.agentservices.protocols

import com.biosimilarity.evaluator.distribution.{PortableAgentCnxn, PortableAgentBiCnxn}
import com.biosimilarity.evaluator.distribution.diesel.DieselEngineScope._
import com.biosimilarity.evaluator.distribution.ConcreteHL.PostedExpr
import com.protegra_ati.agentservices.protocols.msgs._
import com.biosimilarity.lift.model.store.CnxnCtxtLabel
import com.biosimilarity.lift.model.store.CnxnCtxtBranch
import com.biosimilarity.lift.model.store.CnxnCtxtLeaf
import com.biosimilarity.lift.model.store.Factual
import com.biosimilarity.lift.lib._
import scala.util.continuations._
import java.util.UUID

package usage {  
  import com.biosimilarity.evaluator.distribution.diesel.DieselEngineScope._
  import com.biosimilarity.evaluator.distribution.diesel.EvalNodeMapper
  import com.biosimilarity.evaluator.distribution.diesel.DieselEngineCtor
  import com.biosimilarity.evaluator.distribution.diesel.DieselEngineCtor.StdEvalChannel

  import com.biosimilarity.evaluator.distribution.utilities.DieselValueTrampoline._

  import com.biosimilarity.evaluator.distribution.NodeStreams
  import com.biosimilarity.evaluator.distribution.FuzzyStreams
  import com.biosimilarity.evaluator.distribution.FuzzyTerms
  import com.biosimilarity.evaluator.distribution.FuzzyTermStreams

  import com.protegra_ati.agentservices.store.extensions.StringExtensions._

  import com.biosimilarity.lift.model.store.CnxnString
  import scala.concurrent.FJTaskRunnersX  

  trait GLoSStubT
    extends NodeStreams
     with FuzzyStreams
     with FuzzyTerms
     with FuzzyTermStreams
     with CnxnString[String,String,String] 
     with Serializable
  {
    def claimantToGLoS : PortableAgentCnxn
    def verifierToGLoS : PortableAgentCnxn
    def relyingPartyToGLoS : PortableAgentCnxn
    def waitForSignalToInitiateClaim(
      continuation : CnxnCtxtLabel[String,String,String] => SimulationContext
    ) : SimulationContext = {     
      println( "Please state your claim: " )
      val ln = readLine() // Note: this is blocking.
      val claim = ln.toLabel
      println( "your claim, " + claim + ", has been submitted." )
      continuation( claim )      
     }
    def simulateVerifierAckAllowVerificationStep(
      simCtxt : SimulationContext
    ) : SimulationContext = {
      val SimulationContext( node, glosStub, sid, cid, c, v, r, c2v, c2r, v2r, clm ) = simCtxt
      val agntVrfrRd = 
        acT.AgentCnxn( c2v.src, c2v.label, c2v.trgt )
      reset {
        node.publish( agntVrfrRd )(
          AckAllowVerification.toLabel( sid ),
          AckAllowVerification( sid, cid, c2r, clm )
        )
      }
      simCtxt
    }
    def simulateRelyingPartyCloseClaimStep(
      simCtxt : SimulationContext
    ) : SimulationContext = {
      val SimulationContext( node, glosStub, sid, cid, c, v, r, c2v, c2r, v2r, clm ) = simCtxt
      val agntRPRd = 
        acT.AgentCnxn( c2r.trgt, c2r.label, c2r.src )
      
      val witness =
        new CnxnCtxtBranch[String,String,String](
          "verified",
          clm.asInstanceOf[CnxnCtxtLabel[String,String,String] with Factual] :: Nil
        )

      reset {
        node.publish( agntRPRd )(
          CloseClaim.toLabel( sid ),
          CloseClaim( sid, cid, c2v, clm, witness )
        )
      }
      simCtxt
    }

    def waitForVerifierVerificationNotification(
      node : StdEvalChannel,      
      continuation : VerificationMessage => Unit
    ) : Unit = {
      val vrfr2GLoSRd =
        acT.AgentCnxn( verifierToGLoS.src, verifierToGLoS.label, verifierToGLoS.trgt )

      BasicLogService.tweet(
        (
          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          + "\nwaiting for verification notification on: " 
          + "cnxn: " + vrfr2GLoSRd
          + "label: " + VerificationNotification.toLabel
          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        )
      )      

      reset {
        for(
          eVNote <- node.subscribe(
            vrfr2GLoSRd
          )( VerificationNotification.toLabel() )
        ) {
          rsrc2V[VerificationMessage]( eVNote ) match {
            case Left( vmsg@VerificationNotification( sidVN, cidVN, clmntVN, clmVN, witVN ) ) => { 
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nreceived verification notification " + eVNote
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
              continuation( vmsg )
            }
            case Right( true ) => {
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nwaiting for verifier verification notification"
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
            }
            case _ => {
              BasicLogService.tweet(
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nunexpected protocol message : " + eVNote
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            }
          }
        }
      }
    }
    def waitForRelyingPartyVerificationNotification(
      node : StdEvalChannel,
      continuation : VerificationMessage => Unit
    ) : Unit = {
      val rp2GLoSRd =
        acT.AgentCnxn( relyingPartyToGLoS.src, relyingPartyToGLoS.label, relyingPartyToGLoS.trgt )

      BasicLogService.tweet(
        (
          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          + "\nwaiting for verification notification on: " 
          + "cnxn: " + rp2GLoSRd
          + "label: " + VerificationNotification.toLabel
          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        )
      )

      reset {
        for(
          eVNote <- node.subscribe(
            rp2GLoSRd
          )( VerificationNotification.toLabel() )
        ) {
          rsrc2V[VerificationMessage]( eVNote ) match {
            case Left( vmsg@VerificationNotification( sidVN, cidVN, clmntVN, clmVN, witVN ) ) => { 
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nreceived verification notification " + eVNote
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
              continuation( vmsg )
            }
            case Right( true ) => {
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nwaiting for relying party verification notification"
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
            }
            case _ => {
              BasicLogService.tweet(
                "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                + "\nunexpected protocol message : " + eVNote
                + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
              )
            }
          }
        }
      }
    }

    def waitForCompleteClaim(
      node : StdEvalChannel,
      continuation : VerificationMessage => Unit
    ) : Unit = {
      val clmnt2GLoSRd =
        acT.AgentCnxn( claimantToGLoS.trgt, claimantToGLoS.label, claimantToGLoS.src )

      BasicLogService.tweet(
        (
          "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
          + "\nwaiting for complete claim on: " 
          + "cnxn: " + clmnt2GLoSRd
          + "label: " + CompleteClaim.toLabel
          + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
        )
      )
      reset {
        for(
          eCompleteClaim <- node.subscribe(
            clmnt2GLoSRd
          )( CompleteClaim.toLabel() )
        ) {
          rsrc2V[VerificationMessage]( eCompleteClaim ) match {
            case Left( vmsg@CompleteClaim( sidCC, cidCC, vrfrCC, clmCC, witCC ) ) => { 
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nreceived complete claim " + eCompleteClaim
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
              continuation( vmsg )
            }
            case Right( true ) => {
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nwaiting for complete claim"
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
            }
            case _ => {
              BasicLogService.tweet(
                (
                  "||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                  + "\nunexpected protocol message : " + eCompleteClaim
                  + "\n||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||||"
                )
              )
            }
          }
        }
      }
    }
  }    

  case class GLoSStub(
    override val claimantToGLoS : PortableAgentCnxn,
    override val verifierToGLoS : PortableAgentCnxn,
    override val relyingPartyToGLoS : PortableAgentCnxn
  ) extends GLoSStubT 

  case class SimulationContext(
    node : StdEvalChannel,
    glosStub : GLoSStub,
    sid : String,
    cid : String,
    c : PortableAgentCnxn,
    v : PortableAgentCnxn,
    r : PortableAgentCnxn,
    c2v : PortableAgentCnxn,
    c2r : PortableAgentCnxn,
    v2r : PortableAgentCnxn,
    claim : CnxnCtxtLabel[String,String,String]
  )

  object VerificationDriver
   extends NodeStreams
     with FuzzyStreams
     with FuzzyTerms
     with FuzzyTermStreams
     with CnxnString[String,String,String] 
     with FJTaskRunnersX
     with Serializable
  {    
    def nextClaimant() : ClaimantBehavior = {
      ClaimantBehavior()
    }
    def nextVerifier() : VerifierBehavior = {
      VerifierBehavior()
    }
    def nextRelyingParty() : RelyingPartyBehavior = {
      RelyingPartyBehavior()
    }
    def claimantStrm() : Stream[ClaimantBehavior] = {
      tStream( nextClaimant() )( { clmnt => nextClaimant() } )
    }
    def verifierStrm() : Stream[VerifierBehavior] = {
      tStream( nextVerifier() )( { vrfr => nextVerifier() } )
    }
    def relyingPartyStrm() : Stream[RelyingPartyBehavior] = {
      tStream( nextRelyingParty() )( { rp => nextRelyingParty() } )
    }    
    
    def connect(
      selfCnxn1 : PortableAgentCnxn,
      selfCnxn2 : PortableAgentCnxn
    )(
      useSrc : Boolean = true
    ): PortableAgentCnxn = {
      val cnxnLabel = UUID.randomUUID().toString
      if ( useSrc ) {
        PortableAgentCnxn( selfCnxn1.src, cnxnLabel, selfCnxn2.src )
      } 
      else {
        PortableAgentCnxn( selfCnxn1.trgt, cnxnLabel, selfCnxn2.trgt )
      }
    }
    
    def getSelfCnxnStream(
    ) : Stream[PortableAgentCnxn] = {
      val lblStrm =
        for(
          s <- mkRandomLabelStringStream( "l", 0 )
          if !(
            s.contains( "\"" ) 
            || s.contains( "true" )
            || s.contains( "false" )
          )
        ) yield {
          s
        }
      mkSelfCnxnStream(
        new scala.util.Random(),
        lblStrm
      )
    }

    def verificationEnsembleCnxnStrm(
    ) : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)] = {
      val ( cCnxnStrm, vCnxnStrm, rCnxnStrm ) =
        ( getSelfCnxnStream(), getSelfCnxnStream(), getSelfCnxnStream() );
      for(
        ( c, ( v, r ) ) <- cCnxnStrm.zip( vCnxnStrm.zip( rCnxnStrm ) )
      ) yield {
        ( c, v, r, connect( c, v )( true ), connect( c, r )( true ), connect( v, r )( true ) )
      }        
    }
    def verificationEnsembleStrm(
    ) : Stream[(ClaimantBehavior,VerifierBehavior,RelyingPartyBehavior)] = {
      for(
        ( c, ( v, r ) ) <- claimantStrm().zip( verifierStrm().zip( relyingPartyStrm() ) )
      ) yield {
        ( c, v, r )
      }
    }

    trait BehaviorTestStream[Behavior <: ProtocolBehaviorT] {
      def behaviorToGLoS : PortableAgentCnxn
      def behaviorStream : Stream[Behavior]
      def behaviorTestStrm(
        node : StdEvalChannel,
        glosStub : GLoSStub,
        clmntBhvr : Behavior
      )(
        cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
      ): Stream[() => SimulationContext] = {      
        for(
          ( c, v, r, c2v, c2r, v2r ) <- cnxnStrm
        ) yield {
          val agntCRd = 
            acT.AgentCnxn(
              behaviorToGLoS.src,
              behaviorToGLoS.label,
              behaviorToGLoS.trgt
            )
          
          val sid = UUID.randomUUID.toString
          val cid = UUID.randomUUID.toString
          
          () => {          
            glosStub.waitForSignalToInitiateClaim(
              ( claim : CnxnCtxtLabel[String,String,String] ) => {
                spawn { 
                  clmntBhvr.run(
                    node,
                    List[PortableAgentCnxn](
                      behaviorToGLoS
                    ),
                    List[CnxnCtxtLabel[String, String, String]]( )
                  )
                }
                reset {
                  node.publish( agntCRd )(
                    InitiateClaim.toLabel( sid ),
                    InitiateClaim( sid, cid, c2v, c2r, claim )
                  )
                }
                SimulationContext( node, glosStub, sid, cid, c, v, r, c2v, c2r, v2r, claim )
              }
            )          
          }
        }
      }
      
      def behaviorTestStrm(
        node : StdEvalChannel,
        clmntBhvr : Behavior
      )(
        cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
      ): Stream[() => SimulationContext] = {      
        val ( c2GLoSCnxnStrm, v2GLoSCnxnStrm, r2GLoSCnxnStrm ) =
          ( getSelfCnxnStream(), getSelfCnxnStream(), getSelfCnxnStream() );
        val theGLoSCnxnStrm =
          for(
            ( c, ( v, r ) ) <- c2GLoSCnxnStrm.zip( v2GLoSCnxnStrm.zip( r2GLoSCnxnStrm ) )
          ) yield {
            ( c, v, r )
          }       
        theGLoSCnxnStrm.flatMap(
          {
            trpl => {
              behaviorTestStrm(
                node,
                GLoSStub( trpl._1, trpl._2, trpl._3 ),
                clmntBhvr
              )(
                cnxnStrm
              )
            }
          }
        )
      }
      
      def behaviorTestStrm(
        clmntBhvr : Behavior
      )(
        cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
      ): Stream[() => SimulationContext] = {      
        val ndStrm = dslNodeStream
        ndStrm.flatMap(
          {
            node => {
              behaviorTestStrm(
                node, clmntBhvr
              )( cnxnStrm )
            }
          }
        )
      }
      
      def behaviorTestStrm(
        cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
      ): Stream[() => SimulationContext] = {      
        val cStrm = behaviorStream
        cStrm.flatMap(
          {
            clmnt => {
              behaviorTestStrm(
                clmnt
              )(
                cnxnStrm
              )
            }
          }
        )
      }
      
      def behaviorTestStrm(
      ) : Stream[() => SimulationContext] = {      
        behaviorTestStrm(
          verificationEnsembleCnxnStrm()
        )
      }
    }
    
    def verificationEnsembleTestStrm(
      node : StdEvalChannel,
      glosStub : GLoSStub,
      clmntBhvr : ClaimantBehavior,
      vrfrBhvr : VerifierBehavior,
      rpBhvr : RelyingPartyBehavior
    )(
      cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
    ): Stream[() => Unit] = {      
      for(
        ( c, v, r, c2v, c2r, v2r ) <- cnxnStrm
      ) yield {
        val agntCRd = 
          acT.AgentCnxn( c.src, c.label, c.trgt )
        val agntCWr = 
          acT.AgentCnxn( c.trgt, c.label, c.src )

        val sid = UUID.randomUUID.toString
        val cid = UUID.randomUUID.toString

        () => {
          glosStub.waitForSignalToInitiateClaim(
            ( claim : CnxnCtxtLabel[String,String,String] ) => {
              reset {
                node.publish( agntCRd )(
                  InitiateClaim.toLabel( sid ),
                  InitiateClaim( sid, cid, c2v, c2r, claim )
                )
              }
              spawn { 
                clmntBhvr.run(
                  node,
                  List[PortableAgentCnxn](
                    glosStub.claimantToGLoS
                  ),
                  List[CnxnCtxtLabel[String, String, String]]( )
                )
              }
              spawn {
                vrfrBhvr.run(
                  node,
                  List[PortableAgentCnxn](
                    glosStub.verifierToGLoS,
                    c2v
                  ),
                  List[CnxnCtxtLabel[String, String, String]]( )
                )
              }
              spawn {
                rpBhvr.run(
                  node,
                  List[PortableAgentCnxn](
                    glosStub.relyingPartyToGLoS,
                    c2r
                  ),
                  List[CnxnCtxtLabel[String, String, String]]( )
                )
              }
              spawn {
                glosStub.waitForVerifierVerificationNotification(
                  node,
                  { vmsg => println( "Got verifier verification notification!" ) }
                )
              }
              spawn {
                glosStub.waitForRelyingPartyVerificationNotification(
                  node,
                  { vmsg => println( "Got relying party verification notification!" ) }
                )
              }
              spawn {
                glosStub.waitForCompleteClaim(
                  node,
                  { vmsg => println( "Got claimant close claim!" ) }
                )
              }
              SimulationContext( node, glosStub, sid, cid, c, v, r, c2v, c2r, v2r, claim )
            }
          );
          ()
        }                
      }
    }
    def verificationEnsembleTestStrm(
      node : StdEvalChannel,
      clmntBhvr : ClaimantBehavior,
      vrfrBhvr : VerifierBehavior,
      rpBhvr : RelyingPartyBehavior
    )(
      cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
    ): Stream[() => Unit] = {      
      val ( c2GLoSCnxnStrm, v2GLoSCnxnStrm, r2GLoSCnxnStrm ) =
        ( getSelfCnxnStream(), getSelfCnxnStream(), getSelfCnxnStream() );
      val theGLoSCnxnStrm =
        for(
          ( c, ( v, r ) ) <- c2GLoSCnxnStrm.zip( v2GLoSCnxnStrm.zip( r2GLoSCnxnStrm ) )
        ) yield {
          ( c, v, r )
        }       
      theGLoSCnxnStrm.flatMap(
        {
          trpl => {
            verificationEnsembleTestStrm(
              node,
              GLoSStub( trpl._1, trpl._2, trpl._3 ),
              clmntBhvr, vrfrBhvr, rpBhvr
            )(
              cnxnStrm
            )
          }
        }
      )
    }
    def verificationEnsembleTestStrm(
      clmntBhvr : ClaimantBehavior,
      vrfrBhvr : VerifierBehavior,
      rpBhvr : RelyingPartyBehavior
    )(
      cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
    ): Stream[() => Unit] = {      
      val ndStrm = dslNodeStream
      ndStrm.flatMap(
        {
          node => {
            verificationEnsembleTestStrm(
              node, clmntBhvr, vrfrBhvr, rpBhvr
            )( cnxnStrm )
          }
        }
      )
    }
    def verificationEnsembleTestStrm(
      cnxnStrm : Stream[(PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn,PortableAgentCnxn)]
    ): Stream[() => Unit] = {      
      val veStrm = verificationEnsembleStrm
      veStrm.flatMap(
        {
          trpl => {
            verificationEnsembleTestStrm(
              trpl._1, trpl._2, trpl._3
            )(
              cnxnStrm
            )
          }
        }
      )
    }
    def verificationEnsembleTestStrm(
    ): Stream[() => Unit] = {      
      verificationEnsembleTestStrm(
        verificationEnsembleCnxnStrm()
      )
    }
    def testProtocol( 
      numOfTests : Int = 1
    ) : Unit = {
      val testStrm = verificationEnsembleTestStrm.take( numOfTests )
      for( i <- 1 to numOfTests ) {
        val test = testStrm( i - 1 )
        test()
      }
    }
  }
}
