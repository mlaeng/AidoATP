package org.open.payment.alliance.isis.atp;

import java.text.NumberFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.net.Socket;

import org.joda.money.BigMoney;
import org.joda.money.CurrencyUnit;

import com.xeiam.xchange.Exchange;
import com.xeiam.xchange.ExchangeSpecification;
import com.xeiam.xchange.dto.Order.OrderType;
import com.xeiam.xchange.dto.trade.MarketOrder;
import com.xeiam.xchange.service.trade.polling.PollingTradeService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* @author Auberon
* This is the agent that makes trades on behalf of the user.
* 
*/
public class TrendTradingAgent implements Runnable {

	private double trendArrow;
	private double bidArrow;
	private double askArrow;
	private Exchange exchange;
	private PollingTradeService tradeService;
	private ATPTicker lastTick;
	private TrendObserver observer;
	private BigMoney vwap;
	private BigMoney maxBTC;
	private BigMoney minBTC;
	private BigMoney maxLocal;
	private BigMoney minLocal;
	private BigMoney smaShort;
	private BigMoney smaLong;
	private BigMoney emaShort;
	private BigMoney emaLong;
	
	private Double maxWeight;
	private Integer algorithm;
	private Integer tickerSize;
	private CurrencyUnit localCurrency;
	private Logger log;
	private StreamingTickerManager tickerManager;
	
	public TrendTradingAgent(TrendObserver observer) {
		log = LoggerFactory.getLogger(TrendTradingAgent.class);
		this.observer = observer;
		exchange = Application.getInstance().getExchange();
		tradeService = exchange.getPollingTradeService();
		tickerManager = observer.getTickerManager();
		localCurrency = tickerManager.getCurrency();
		maxBTC = BigMoney.of(CurrencyUnit.of("BTC"),new BigDecimal(Application.getInstance().getConfig("MaxBTC")));
		maxLocal = BigMoney.of(localCurrency,new BigDecimal(Application.getInstance().getConfig("MaxLocal")));
		minBTC = BigMoney.of(CurrencyUnit.of("BTC"),new BigDecimal(Application.getInstance().getConfig("MinBTC")));
		minLocal = BigMoney.of(localCurrency,new BigDecimal(Application.getInstance().getConfig("MinLocal")));
		maxWeight = new Double(Application.getInstance().getConfig("MaxLoss"));
		algorithm = new Integer(Application.getInstance().getConfig("Algorithm"));		
	}

	public void run(){
		
		boolean adsUp = false, adsDown = false;
		boolean emaUp = false, emaDown = false;
		boolean smaUp = false, smaDown = false;
		boolean evalAsk = false, evalBid = false;
		boolean useADS = Application.getInstance().getConfig("UseADS").equals("1");
		boolean useSMA = Application.getInstance().getConfig("UseSMA").equals("1");
		boolean useEMA = Application.getInstance().getConfig("UseEMA").equals("1");
		NumberFormat numberFormat = NumberFormat.getNumberInstance();
		
		numberFormat.setMaximumFractionDigits(8);
		
		trendArrow = observer.getTrendArrow();
		bidArrow = observer.getBidArrow();
		askArrow = observer.getAskArrow();
		vwap = observer.getVwap();
		lastTick = observer.getLastTick();
		tickerSize = observer.getTickerSize();
		
		StringBuilder str = new StringBuilder();
		str.append("Ticker Size: ");
		str.append(tickerSize);
		str.append(" | ");
		str.append("Trend Arrow: ");
		str.append(trendArrow);
		str.append(" | ");
		str.append("Bid Arrow: ");
		str.append(bidArrow);
		str.append(" | ");
		str.append("Ask Arrow: ");
		str.append(askArrow);
		str.append(" | ");
		str.append("VWAP: ");
		str.append(vwap);
		log.info(str.toString());
		
		emaLong=observer.getLongEMA();
		emaShort=observer.getShortEMA();
		smaLong=observer.getLongSMA();
		smaShort=observer.getShortSMA();
		
		str.setLength(0);
		str.append("Long EMA: ");
		str.append(localCurrency.getCode()+" "+numberFormat.format(emaLong.getAmount()));
		str.append(" | ");
		str.append("Short EMA: ");
		str.append(localCurrency.getCode()+" "+numberFormat.format(emaShort.getAmount()));
		str.append(" | ");
		str.append("Long SMA: ");
		str.append(smaLong.toString());
		str.append(" | ");
		str.append("Short SMA: ");
		str.append(smaShort.toString());

		log.info(str.toString());
		
		// if Advance/Decline Spread algorithm is enabled, use it to decide trade action
		if (useADS){
			if(trendArrow > 0 && bidArrow > 0){
				//If market is trending up, we should look at selling
				adsUp = true;
			}else if(trendArrow < 0 && askArrow < 0){
				//If market is trending down, we should look at buying
				adsDown = true;
			}
			str.setLength(0);
			str.append("Advance/Decline spread has determined that the ");
			str.append(localCurrency.getCode());
			str.append(" market is trending");
			if(trendArrow > 0) {
				//Market is going up, look at selling some BTC
				str.append(" up.");
				
			}else if(trendArrow < 0) {
				//Market is going down, look at buying some BTC
				str.append(" down.");
			}else {
				//Market is stagnant, hold position
				str.append(" flat.");
			}
			log.info(str.toString());
		}
		
		// if EMA algorithm is enabled, use it to decide trade action
		if (useEMA){
			if(emaShort.isGreaterThan(emaLong)){
				//If market is trending up, we should look at selling
				emaUp = true;
			}else if(emaShort.isLessThan(emaLong)){
				//If market is trending down, we should look at buying
				emaDown = true;
			}
			str.setLength(0);
			str.append("EMA has determined that the ");
			str.append(localCurrency.getCode());
			str.append(" market is trending");
			if(emaUp) {
				//Market is going up, look at selling some BTC
				str.append(" up.");
			}else if(emaDown) {
				//Market is going down, look at buying some BTC
				str.append(" down.");
			}else {
				//Market is stagnant, hold position
				str.append(" flat.");
			}
			log.info(str.toString());
		}
		
		// if SMA algorithm is enabled, use it to decide trade action
		if (useSMA){
			if(smaShort.isGreaterThan(smaLong)){
				//If market is trending up, we should look at selling
				smaUp = true;
			}else if(smaShort.isLessThan(smaLong)){
				//If market is trending down, we should look at buying
				smaDown = true;
			}
			str.setLength(0);
			str.append("SMA has determined that the ");
			str.append(localCurrency.getCode());
			str.append(" market is trending");
			if(smaUp) {
				//Market is going up, look at selling some BTC
				str.append(" up.");
			}else if(smaDown) {
				//Market is going down, look at buying some BTC
				str.append(" down.");
			}else {
				//Market is stagnant, hold position
				str.append(" flat.");
			}
			log.info(str.toString());
		}
		
		try {
			// Look to Buy if :
			// AD spread is trending up and EMA & SMA are disabled
			//		or
			// AD spread is trending up and EMA is trending down and SMA is disabled
			// 		or
			// AD spread is trending up and EMA is trending down and SMA is trending down
			// 		or
			// AD spread is trending up and EMA is disabled and SMA is trending down
			// 		or
			// AD spread is disabled and EMA is trending up and SMA is trending down
			// 		or
			// AD spread is disabled and EMA is trending up and SMA is disabled
			// 		or
			// AD spread is disabled and EMA is disabled SMA is trending up
			
			evalAsk = (adsUp && ((!useSMA && (emaDown || !useEMA)) || (smaDown && (!useEMA || emaDown)))) || (!useADS && ((emaUp && (smaDown || !useSMA)) || (!useEMA && smaUp)));
			
			// Look to Sell if :
			// AD spread is trending down and EMA & SMA are disabled
			//		or
			// AD spread is trending down and EMA is trending up and SMA is disabled
			//		or
			// AD spread is trending down and EMA is trending up and SMA is trending up
			// 		or
			// AD spread is trending down and EMA is disabled and SMA is trending up
			//		or
			// AD spread is disabled and EMA is trending down and SMA is trending up
			// 		or
			// AD spread is disabled and EMA is trending down and SMA is disabled
			// 		or
			// AD spread is disabled and EMA is disabled SMA is trending down
			
			evalBid = (adsDown && ((!useEMA && (smaUp || !useSMA)) || (emaUp && (!useSMA || smaUp)))) || (!useADS && ((emaDown && (smaUp || !useSMA)) || (!useEMA && smaDown)));
			
			if (evalAsk) {
				evalAsk();
			}else if (evalBid) {
				evalBid();
			}else {
				log.info("Trend following trading agent has decided no "+localCurrency.getCode()+" action will be taken at this time.");
			}
		} catch (Exception e) {
				log.error("ERROR: Caught unexpected exception, shutting down trend following trading agent now!. Details are listed below.");
				e.printStackTrace();
			}
	}

	//Let's decide whether or not to sell & how much to sell 
	private void evalAsk(){
		//Look at current bid
		BigMoney currentBid = lastTick.getBid();
		
		//Is currentBid > averageCost?
		if(currentBid.isGreaterThan(vwap)) {
			
			//Check balance and see if we even have anything to sell
			
			try {
				
				Double weight;
				//Look at bid arrow and calculate weight
				if(algorithm == 1) {
					weight = (bidArrow + trendArrow) / tickerSize;
				}else {
					weight = bidArrow / tickerSize * trendArrow / tickerSize;
				}
				
				log.info("Calculated weight is "+weight);
				weight = Math.abs(weight);
				
				if(weight > maxWeight) {
					log.info("Weight is above stop loss value, limiting weight to "+maxWeight);
					weight = maxWeight;
				}
				
				BigMoney balanceBTC = AccountManager.getInstance().getBalance(CurrencyUnit.of("BTC"));
				
				if (balanceBTC != null) {
					log.debug("BTC Balance: "+balanceBTC.toString());
				}else {
					log.error("ERROR: BTC Balance is null");
				}
				if (maxBTC != null) {
					log.debug("Max. BTC: "+maxBTC.toString());
				}else {
					log.error("ERROR: Max. BTC is null");
				}
				if (minBTC != null) {
					log.debug("Min. BTC: "+minBTC.toString());
				}else {
					log.error("ERROR: Min. BTC is null");
				}
								
				if(balanceBTC != null && maxBTC != null && minBTC != null) {
					
					if(balanceBTC.isZero()) {
						log.info("BTC balance is empty. No further selling is possible until the market corrects or funds are added to your account.");
						return;
					}
					
					BigMoney qtyToSell;
					BigDecimal bigWeight = new BigDecimal(weight);
					if(algorithm == 1) {
						qtyToSell = balanceBTC.multipliedBy(bigWeight);
					}else {
						if(balanceBTC.compareTo(maxBTC) >= 0) {
							qtyToSell = maxBTC.multipliedBy(bigWeight);
						}else {
							qtyToSell = balanceBTC.multipliedBy(bigWeight);
						}
					}
					
					log.info("Trend following trade agent is attempting to sell "+qtyToSell.withScale(8,RoundingMode.HALF_EVEN).toString()+" of "+balanceBTC.toString()+" available");
					if(qtyToSell.isGreaterThan(maxBTC)) {
						log.info(qtyToSell.withScale(8,RoundingMode.HALF_EVEN).toString() + " was more than the configured limit of "+maxBTC.toString());
						log.info("Reducing order size to "+maxBTC.toString());
						qtyToSell = maxBTC;
					}
					if(qtyToSell.isLessThan(minBTC)) {
						log.info(qtyToSell.withScale(8,RoundingMode.HALF_EVEN).toString() + " was less than the configured limit of "+minBTC.toString());
						log.info("Trend following trade agent has decided that there is not enough "+localCurrency.getCode()+" momentum to trade at this time.");
						return;
					}
					if (!ArbitrageEngine.getInstance().getDisableTrendTrade()) {
						marketOrder(qtyToSell.getAmount(),OrderType.ASK);
					} else {
						log.info("Trend following trades disabled by Arbitrage Engine.");
					}
				}else{
					log.info("Could not determine wallet balance at this time, order will not be processed.");
				}
			}catch(WalletNotFoundException e) {
				log.error("ERROR: Could not find wallet for "+localCurrency.getCurrencyCode());
				System.exit(1);
			}
			
		}else{
			log.info("Current bid price of "+currentBid.toString()+" is below the VWAP of "+vwap.toString());
			log.info("Trend following trade agent has determined that "+localCurrency.getCurrencyCode()+" market conditions are not favourable for you to sell at this time.");
		}
	}
	
	//Decide whether or not to buy and how much to buy
	private void evalBid(){
		//Look at current ask
		BigMoney currentAsk = lastTick.getAsk();
		if(currentAsk.isLessThan(vwap)) {
			//Formula for bid is the same as for ASK with USD/BTC instead of BTC/USD
			Double weight;
			
			//Look at bid arrow and calculate weight
			if(algorithm == 1) {
				weight = (askArrow + trendArrow) / tickerSize;
			}else {
				weight = askArrow / tickerSize * trendArrow / tickerSize;
			}
			
			weight = Math.abs(weight);
			
			log.info("Calculated weight is "+weight);
			BigDecimal bigWeight = new BigDecimal(weight);			
			if(weight > maxWeight) {
				log.info("Weight is above stop loss value, limiting weight to "+maxWeight);
				weight = maxWeight;
			}
			
			BigMoney balanceLocal;
			try {
				
				balanceLocal = AccountManager.getInstance().getBalance(localCurrency);
				
				if (balanceLocal != null) {
					log.debug("Local Balance: "+balanceLocal.toString());
				}else {
					log.error("ERROR: Local Balance is null");
				}
				if (maxLocal != null) {
					log.debug("Max. Local: "+maxLocal.toString());
				}else {
					log.error("ERROR: Max. Local is null");
				}
				if (minLocal != null) {
					log.debug("Min. Local: "+minLocal.toString());
				}else {
					log.error("ERROR: Min. Local is null");
				}
				
				if(balanceLocal != null && maxLocal != null && minLocal != null) {
						
					if(balanceLocal.isZero()) {
						log.info(localCurrency+" balance is empty until the market corrects itself or funds are added to your account.");
						return;
					}
					
					BigMoney qtyToBuy;
					bigWeight = new BigDecimal(weight);
					if(algorithm == 1) {
						qtyToBuy = balanceLocal.multipliedBy(bigWeight);
					}else {
						if(balanceLocal.compareTo(maxLocal) >= 0) {
							qtyToBuy = maxLocal.multipliedBy(bigWeight);
						}else {
							qtyToBuy = balanceLocal.multipliedBy(bigWeight);
						}
					}
					
					log.info("Attempting to buy "+qtyToBuy.withScale(8,RoundingMode.HALF_EVEN).toString());
					if(qtyToBuy.isGreaterThan(maxLocal)){
						log.info(qtyToBuy.withScale(8,RoundingMode.HALF_EVEN).toString() +" was more than the configured maximum of "+maxLocal.toString()+". Reducing order size to "+maxLocal.toString());
						qtyToBuy = maxLocal;
					}
					
					if(qtyToBuy.isLessThan(minLocal)){
						log.info(qtyToBuy.withScale(8,RoundingMode.HALF_EVEN).toString() + " was less than the configured minimum of "+minLocal.toString());
						log.info("There just isn't enough momentum to trade at this time.");
						return;
					}
					if (!ArbitrageEngine.getInstance().getDisableTrendTrade()) {
						marketOrder(qtyToBuy.getAmount(),OrderType.BID);
					} else {
						log.info("Trend following trades disabled by Arbitrage Engine.");
					}
				}
			} catch (WalletNotFoundException e) {
				log.error("ERROR: Could not find wallet for "+localCurrency.getCurrencyCode());
				System.exit(1);
			}	
		}else{
			log.info("Current ask price of "+currentAsk.toString()+" is above the VWAP of "+vwap.toString());
			log.info("The trading agent has determined that "+localCurrency.getCurrencyCode()+" market conditions are not favourable for you to buy at this time.");
		}
	}
	
	private void marketOrder(BigDecimal qty, OrderType orderType) {
		MarketOrder order = new MarketOrder(orderType,qty,"BTC",localCurrency.getCurrencyCode());
		boolean success = true;
		
		if(!Application.getInstance().getSimMode()){
			String marketOrderReturnValue = tradeService.placeMarketOrder(order);
			log.info("Market Order return value: " + marketOrderReturnValue);
			success=(marketOrderReturnValue != null) ? true:false;
		}else{
			log.info("You were in simulation mode, the trade below did NOT actually occur.");
		}
		
		String action,failAction;
		if(orderType == OrderType.ASK) {
			action = " sold ";
			failAction = " sell ";
		}else {
			action = " bought ";
			failAction = " buy ";
		}
		
		if(success){
			log.info("Successfully"+action+qty.toPlainString()+" at current market price.");
			PLModel localProfit = AccountManager.getInstance().getPLFor(localCurrency);
			PLModel btcProfit = AccountManager.getInstance().getPLFor(CurrencyUnit.of("BTC"));
			
			log.info("Current P/L: "+btcProfit.getAmount()+" | "+btcProfit.getPercent()+"%");
			//log.info("Current P/L: "+localProfit.getAmount()+" | "+localProfit.getPercent()+"%" );
			
			Double overall;
			Double btc = btcProfit.getAmount().getAmount().doubleValue();
			Double local = localProfit.getAmount().getAmount().doubleValue();
			Double btcNormalized = btc * lastTick.getLast().getAmount().doubleValue();
			overall = local + btcNormalized;
			log.info("Overall P/L: "+overall+" "+localCurrency.getCurrencyCode());
			log.info(AccountManager.getInstance().getAccountInfo().toString());			
		}else{
			log.error("ERROR: Failed to"+failAction+qty.toPlainString()+" at current market price. Please investigate");
		}
	}
}
