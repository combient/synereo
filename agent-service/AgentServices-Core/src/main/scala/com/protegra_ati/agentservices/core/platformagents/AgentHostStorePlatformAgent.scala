package com.protegra_ati.agentservices.core.platformagents

/*
 * This is meant to be used as a go between for the Host UI to DB, Host UI to Public for certain things like Gets
 */

import com.protegra_ati.agentservices.core.platformagents.behaviors._
import com.protegra.agentservicesstore.extensions.StringExtensions._
import com.protegra.agentservicesstore.extensions.ResourceExtensions._
import com.protegra.agentservicesstore.extensions.URIExtensions._
import com.protegra_ati.agentservices.core.events._
import com.protegra_ati.agentservices.core.messages._
import com.protegra.agentservicesstore.AgentTS._
import com.protegra.agentservicesstore.AgentTS.acT._
import com.protegra_ati.agentservices.core.schema._
import com.protegra_ati.agentservices.core.messages.content._
import com.protegra_ati.agentservices.core.messages.login._
import invitation._
import introduction._
//import referral._
//import registration._
//import search._
import verifier._
import com.protegra.agentservicesstore.util.Severity

import java.net.URI
import net.lag.configgy._

import scala.util.continuations._
import java.lang.Boolean
import com.protegra_ati.agentservices.core.schema._
import org.joda.time.{DateTime, Instant}
import java.util.{UUID, HashMap}
import scala.collection.mutable._
import com.biosimilarity.lift.lib.moniker._
import java.lang.reflect._

class AgentHostStorePlatformAgent extends BasePlatformAgent
with Storage
with ResultStorage
with Public
with Private
with HostedConnections
with Notifications
with Authorization
with Auditing

with ContentRequestSet
with ContentRequestSetPrivate
with ContentResponseSet
//with SearchRequestSet
//with SearchRequestSetPrivate
//with SearchResponseSet
with LoginRequestSet
with LoginRequestSetPrivate
with LoginResponseSet
with VerifierRequestSet
with VerifierResponseSet
with VerifierRequestSetPrivate
with ConnectionBroker
with InvitationRequestSet
with InvitationResponseSet
with InvitationRequestSetPrivate
with InvitationResponseSetCreatorPrivate //exception to the rule, store listens to a private response but of type creator
with IntroductionRequestSet
with IntroductionResponseSet
with IntroductionRequestSetPrivate
with IntroductionResponseSetCreatorPrivate //exception to the rule, store listens to a private response but of type creator
//with ReferralRequestSet
//with ReferralResponseSet
//with ReferralRequestSetPrivate
//with RegistrationRequestSet
//with RegistrationResponseSet
//with RegistrationRequestSetPrivate
//with RegistrationResponseSetCreatorPrivate //exception to the rule, store listens to a private response but of type creator

//with AgentHostStoreTestData
with MessageStore
//with OnStoreSearchProcessor
//with WatchListListener
//with Notifier
{
  var _storeCnxn: AgentCnxnProxy = null
  var _cnxnUIStore = new AgentCnxnProxy("UI".toURI, "", "Store".toURI)
  val BIZNETWORK_AGENT_ID = "f5bc533a-d417-4d71-ad94-8c766907381b"

  //hack for testing
  var _cnxnRAVerifier: AgentCnxnProxy = null
  var _cnxnCAVerifier: AgentCnxnProxy = null

  var _forwardedMessages = new HashMap[ String, Message ]

  //this is needed to prevent duplicates until the get consume from cache and db issue has been fixed
  //  var _processedMessages = new HashSet[ String ]

  var _verifyRequests = new HashMap[ String, VerifyRequest ]

  override def init(configUtil: Config)
  {
    initPrivate(configUtil)
    initDb(configUtil)
    initResultDb(configUtil)
    initPublic(configUtil)

    _storeCnxn = new AgentCnxnProxy(this._id.toString.toURI, "", this._id.toString.toURI)
  }

  def initForTest(publicAddress: URM, publicAcquaintanceAddresses: List[ URM ], privateAddress: URM, privateAcquaintanceAddresses: List[ URM ], dbAddress: URM, resultAddress: URM, id: UUID)
  {
    initPublic(publicAddress, publicAcquaintanceAddresses)
    initPrivate(privateAddress, privateAcquaintanceAddresses)
    initDb(dbAddress)
    initResultDb(resultAddress)

    _storeCnxn = new AgentCnxnProxy(id.toString.toURI, "", id.toString.toURI)
    super.initForTest(id)
  }

  override def loadQueues()
  {
    loadStorageQueue()
    loadResultStorageQueue()
    loadPrivateQueue()

    if (isDistributedNetworkMode)
      loadPublicQueue()
    //the same for now, should be initialized properly to separate queues

    loadUserCnxnList()
  }

  //does not raise events but sends messages back to AgentHostUIPlatformAgent
  def startListening() =
  {
    report("IN THE STORE LISTEN", Severity.Trace)

    if (isDistributedNetworkMode)
      listenPublicRequests(_storeCnxn)

    listenForUICnxns()

    //    listenForVerifierCnxns()
    if (isDistributedNetworkMode)
      listenForHostedCnxns()

    // watch list observation starts
    //observeWatchLists()
  }

  protected def listenForUICnxns() =
  {
    //ui store
    //all responses come on individual cnxns
    //    listenPublicResponses(_cnxnUIStore)

    listenPrivateContentRequest(_cnxnUIStore)
//    listenPrivateSearchRequest(_cnxnUIStore)
    listenPrivateLoginRequest(_cnxnUIStore)
    listenPrivateVerifierResponse(_cnxnUIStore)
    listenPrivateInvitationRequest(_cnxnUIStore)
    listenPrivateInvitationCreatorResponses(_cnxnUIStore)
//    listenPrivateReferralRequest(_cnxnUIStore)
//    listenPrivateRegistrationRequest(_cnxnUIStore)
//    listenPrivateRegistrationCreatorResponses(_cnxnUIStore)
    //intros on hold for now
    //    listenPrivateIntroductionRequest(_cnxnUIStore)
    //    listenPrivateIntroductionCreatorResponses(_cnxnUIStore)
  }

  protected def listenForVerifierCnxns() =
  {
    //hack for tests
    //    if ( _cnxnRAVerifier != null )
    //      listen(_msgQ, _cnxnRAVerifier, Channel.Verify, ChannelType.Response, handlePublicVerifyResponseChannel(_: AgentCnxnProxy, _: Message))
    //
    //    if ( _cnxnCAVerifier != null )
    //      listen(_msgQ, _cnxnCAVerifier, Channel.Verify, ChannelType.Request, handlePublicVerifyRequestChannel(_: AgentCnxnProxy, _: Message))
  }

  def listenPublicRequests(cnxn: AgentCnxnProxy)
  {
    listenPublicContentRequest(cnxn)
//    listenPublicSearchRequest(cnxn)
//    listenPublicSearchRequestOnStore(cnxn)
    listenPublicLoginRequest(cnxn)
    listenPublicVerifierRequest(cnxn)

    //listen invitation creator on jen_broker, ie jen_mike. jen is the broker for mike in this case
    listenPublicInvitationCreatorRequests(cnxn)
    //listen invitation consumer on broker_jen, ie mike_jen. mike is the broker for jen in this case
    listenPublicInvitationConsumerRequests(cnxn)
//    listenPublicReferralRequests(cnxn)
//    listenPublicRegistrationCreatorRequests(cnxn)
//    listenPublicRegistrationConsumerRequests(cnxn)
    //Intros on hold for now
    //listen introduction creator on jen_broker, ie jen_mike. jen is the broker for mike in this case
    //    listenPublicIntroductionCreatorRequests(cnxn)
    //listen introduction consumer on broker_jen, ie mike_jen. mike is the broker for jen in this case
    //    listenPublicIntroductionConsumerRequests(cnxn)
  }

  def listenPublicResponses(cnxn: AgentCnxnProxy)
  {
    if (isDistributedNetworkMode){

      listenPublicContentResponse(cnxn)
  //    listenPublicSearchResponse(cnxn)
      listenPublicLoginResponse(cnxn)
      listenPublicVerifierResponse(cnxn)

      //listen invitation consumer on broker_jen, ie mike_jen. mike is the broker for jen in this case
      listenPublicInvitationConsumerResponses(cnxn)
      listenPublicInvitationCreatorResponses(cnxn)
      listenPublicIntroductionConsumerResponses(cnxn)
  //    listenPublicReferralResponses(cnxn)
  //    listenPublicRegistrationConsumerResponses(cnxn)
  //    listenPublicRegistrationCreatorResponses(cnxn)
    }
  }

  def sendPrivate(cnxn: AgentCnxnProxy, msg: Message)
  {
    report("!!! Received on Public channel...Sending on privateQ!!!: " + " channel: " + msg.getChannelKey + " cnxn: " + msg.originCnxn, Severity.Info)
    msg.channelLevel = Some(ChannelLevel.Private)
    send(_privateQ, _cnxnUIStore, msg)
  }

  override def send(queue: PartitionedStringMGJ, cnxn: AgentCnxnProxy, msg: Message)
  {
    msg match {
      case x: CreateInvitationRequest => {
        capture(cnxn, x)
      }
//      case x: AffiliateRequest => {
//        capture(cnxn, x)
//      }
//      case x: ApproveRegistrationRequest => {
//        //an exception where it comes back on the inverse
//        capture(x.originCnxn, x)
//      }
      //      case x: CreateIntroductionRequest => {
      //        capture(cnxn, x)
      //      }
      case _ => {/*ignore*/}
    }


    if (msg.channelLevel == Some(ChannelLevel.Public) && isLocalNetworkMode())
      processPublicSendLocally(cnxn, msg)
    else
      super.send(queue, cnxn, msg)
  }

  /**
   * To improve performance, when a single store PA is being used, no reason to send requests out onto public q.
   * The send method (above) intercepts messages that would normally be sent on public queue, and instead sends them to this method
   * which directs the message directly to the local handler, thus avoiding a round trip to the public q.
   */
  protected def processPublicSendLocally(cnxn: AgentCnxnProxy, msg: Message)
  {
    report("In processSendLocally", Severity.Trace)

    //TODO remove following println
    System.err.println(System.currentTimeMillis() + "In processSendLocally - channel = " + msg.channel + ", type = " + msg.channelType + ", cnxn= " + cnxn);

    if (msg.channelType == ChannelType.Response){
      //TODO remove following println
      System.err.println(System.currentTimeMillis() +  "SendPrivate called - thread = " + Thread.currentThread().getName);
      sendPrivate(cnxn, msg)
    }

    if (msg.channelType == ChannelType.Request){
      if (msg.channel == Channel.Content )
        handlePublicContentRequestChannel(cnxn, msg)
      else
      if (msg.channel == Channel.Security)
        handlePublicSecurityRequestChannel(cnxn, msg)
      else
      if (msg.channel == Channel.Verify)
        handleVerifyRequestChannel(cnxn, msg)
      else
      if (msg.channel == Channel.Invitation && msg.channelRole == Some(ChannelRole.Creator))
        handlePublicInvitationCreatorRequestChannel(cnxn, msg)
      else
      if (msg.channel == Channel.Invitation && msg.channelRole == Some(ChannelRole.Consumer))
        handlePublicInvitationConsumerRequestChannel(cnxn, msg)
      else{
        //        System.err.println("In processSendLocally, received request for which there is no handler, type = " + msg.getClass.getName);
        report("Received request for which there is no handler, message type = " + msg.getClass.getName, Severity.Error)
      }
    }

  }


}