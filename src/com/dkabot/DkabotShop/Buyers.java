package com.dkabot.DkabotShop;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.PagingList;
import com.avaje.ebean.Query;

public class Buyers implements CommandExecutor {
	private DkabotShop plugin;
	public Buyers(DkabotShop plugin) {
		this.plugin = plugin;
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!(sender instanceof Player)) {
			sender.sendMessage(ChatColor.RED + "Sorry, console command usage is not yet supported!");
			return true;
		}
		//Code for /buy
		if(cmd.getName().equalsIgnoreCase("buy")) {
			//Player check
			if (!(sender instanceof Player)) {
				sender.sendMessage(ChatColor.RED + "Only players can buy items!");
				return true;
			}
			//Permission Check
			if(!sender.hasPermission("dkabotshop.buy")) {
				sender.sendMessage(ChatColor.RED + "You lack permission to do this.");
				return true;
			}
			//Argument length validation
			if(args.length < 2) {
				sender.sendMessage(ChatColor.RED + "Too few arguments.");
				return true;
			}
			if(args.length > 3) {
				sender.sendMessage(ChatColor.RED + "Too many arguments.");
				return true;
			}
			//Declare variables
			ItemStack material;
			Integer amount = null;
			Integer lastSellerAmount = null;
			Integer amountRemaining = null;
			Integer prevAmountRemaining = null;
			Double totalCost = 0.00;
			Double maxPrice = null;
			Integer amountGiven = 0;
			Integer sellers;
			Player player = (Player) sender;
			String currencyName = "Error Getting Currency";
			//Item validation
			material = plugin.getMaterial(args[0], true, player);
			if(material == null) {
				sender.sendMessage(ChatColor.RED + "Invalid item!");
				return true;
			}
			//Nice try, but still no
			if(plugin.illegalItem(material)) {
				sender.sendMessage(ChatColor.RED + "Disallowed item!");
				return true;
			}
			//Amount setting and validation
			if(!plugin.isInt(args[1])) {
				sender.sendMessage(ChatColor.RED + "Amount to buy must be a number.");
				return true;
			}
			amount = Integer.parseInt(args[1]);
			amountRemaining = amount;
			if(amount <= 0) {
				sender.sendMessage(ChatColor.RED + "Amount to buy cannot be 0 or negative.");
				return true;
			}
			//Set max cost if specified
			if(args.length == 3) {
				maxPrice = plugin.getMoney(args[2]);
				//Validate max cost
				if(maxPrice == null) {
					sender.sendMessage(ChatColor.RED + "Invalid maximum purchase price!");
					return true;
				}
				if(maxPrice <= 0) {
					sender.sendMessage(ChatColor.RED + "Max purchase price cannot be 0 or negative!");
					return true;
				}
			}
			//Get all instances of this item for sale, save for the ones sold by the buyer
			List<DB_ForSale> DBClass = plugin.getDatabase().find(DB_ForSale.class).where().eq("item", material.getType().toString()).ne("seller", sender.getName()).orderBy().asc("cost").findList();
			//Loop through all the entries
			for(sellers = 0; sellers < DBClass.size();) {
				//Get the specific entry
				DB_ForSale tmpDB = DBClass.get(sellers);
				//Max price check, if applicable
				if(maxPrice != null && tmpDB.getCost() > maxPrice) break;
				//Previous amount remaining to be bought, for calculations
				prevAmountRemaining = amountRemaining;
				//Set amount remaining
				amountRemaining = amountRemaining - tmpDB.getAmount();
				//If the amount to buy has been reached
				if(amountRemaining <= 0) {
					//Set new amount in the shop
					lastSellerAmount = prevAmountRemaining;
					//Add to the total cost for the buyer
					totalCost = totalCost + prevAmountRemaining * tmpDB.getCost();
					break;
				}
				else {
					//Add to the total cost for the buyer
					totalCost = totalCost + tmpDB.getAmount() * tmpDB.getCost();
				}
				//Continue through the loop
				sellers++;
			}
			//Check if not even one item fit the criteria
			if(amountRemaining == amount) {
				sender.sendMessage(ChatColor.RED + "Nobody is selling this!");
				return true;
			}
			//Check if not enough items fit the criteria
			if(amountRemaining > 0) {
				sender.sendMessage(ChatColor.RED + "There isn't enough for sale!");
				return true;
			}
			//Check if the buyer has enough funds
			if(plugin.economy.getBalance(sender.getName()) < totalCost) {
				sender.sendMessage(ChatColor.RED + "You lack enough funds, you need " + ChatColor.YELLOW + (totalCost - plugin.economy.getBalance(sender.getName())) + ChatColor.RED + " more Sky Coins!");
				return true;
			}
			//Finally, the transaction actually takes place!
			//Give the player items
			for(Integer i = 0; i < sellers + 1;) {
				if(i < sellers) {
					DB_ForSale tmpDB = DBClass.get(i);
					amountGiven = amountGiven + tmpDB.getAmount();
					Integer amountNotReturned = plugin.giveItem(new ItemStack(material.getType(), tmpDB.getAmount(), material.getType().getMaxDurability(), material.getData().getData()), player);
					if(amountNotReturned != 0) {
						sender.sendMessage(ChatColor.RED + "You lack enough space for this!");
						player.getInventory().removeItem(new ItemStack(material.getType(), amountGiven - amountNotReturned, material.getType().getMaxDurability(), material.getData().getData()));
						return true;
					}
				}
				else {
					amountGiven = amountGiven + lastSellerAmount;
					Integer amountNotReturned = plugin.giveItem(new ItemStack(material.getType(), lastSellerAmount, material.getType().getMaxDurability(), material.getData().getData()), player);
					if(amountNotReturned != 0) {
						sender.sendMessage(ChatColor.RED + "You lack enough space for this!");
						player.getInventory().removeItem(new ItemStack(material.getType(), amountGiven - amountNotReturned, material.getType().getMaxDurability(), material.getData().getData()));
						return true;
					}
				}
				i++;
			}
			//Take funds from the player
			plugin.economy.withdrawPlayer(sender.getName(), totalCost);
			for(Integer i = 0; i < sellers;) {
				DB_ForSale tmpDB = DBClass.get(i);
				//For any and all sellers sold out, give them money and remove their DB entry
				plugin.economy.depositPlayer(tmpDB.getSeller(), tmpDB.getAmount() * tmpDB.getCost());
				Player seller = Bukkit.getServer().getPlayer(tmpDB.getSeller());
				if(seller != null) seller.sendMessage(ChatColor.GOLD + sender.getName() + ChatColor.BLUE + " bought all of your shop's " + ChatColor.GOLD + material.getType().toString() + ChatColor.BLUE + "!");
				plugin.getDatabase().delete(tmpDB);
				i++;
			}
			//Set shop amount for final seller and give them money
				DB_ForSale finalSellerDB = DBClass.get(sellers);
				if(finalSellerDB.getAmount() - lastSellerAmount <= 0) plugin.getDatabase().delete(finalSellerDB);
				else finalSellerDB.setAmount(finalSellerDB.getAmount() - lastSellerAmount);
				plugin.economy.depositPlayer(finalSellerDB.getSeller(), lastSellerAmount * finalSellerDB.getCost());
				Player finalSeller = Bukkit.getServer().getPlayer(finalSellerDB.getSeller());
				if(finalSeller != null) finalSeller.sendMessage(ChatColor.GOLD + sender.getName() + ChatColor.BLUE + " bought " + ChatColor.GOLD + lastSellerAmount + ChatColor.BLUE + " of your shop's " + ChatColor.GOLD + material.getType().toString() + ChatColor.BLUE + "!");
				plugin.getDatabase().save(DBClass);
				//Get a new instance of the Transaction Logging table and log the transaction
				DB_History transactionLog = new DB_History();
				transactionLog.setAmount(amount);
				transactionLog.setBuyer(sender.getName());
				transactionLog.setItem(material.getType().toString());
				transactionLog.setTotalCost(totalCost);
				transactionLog.setCost(totalCost / amount);
				plugin.getDatabase().save(transactionLog);
				if(totalCost == 1) currencyName = plugin.economy.currencyNameSingular();
				else currencyName = plugin.economy.currencyNamePlural();
				sender.sendMessage(ChatColor.GREEN + "Successfully bought " + amount + " " + material.getType().toString() + ". Total cost: " + totalCost + " " + currencyName);
			//If you get here, success!
			return true;
		}
		
		//Code for /find
		if(cmd.getName().equalsIgnoreCase("stock")) {
			//Permission Check
			if(!sender.hasPermission("dkabotshop.stock")) {
				sender.sendMessage(ChatColor.RED + "You lack permission to do this.");
				return true;
			}
			//Check input length
			if(args.length > 3) {
				sender.sendMessage(ChatColor.RED + "Too many arguments.");
				return true;
			}
			//Declare Variables
			Player player = (Player) sender;
			String seller = "";
			PagingList<DB_ForSale> DBPageList = null;
			ItemStack material = null;
			List<DB_ForSale> DBClass = null;
			Integer hyphenCount;
			Integer page = 0;
			String hyphens = "";
			Query<DB_ForSale> query = plugin.getDatabase().find(DB_ForSale.class).orderBy().desc("id");
			ExpressionList<?> eList = query.where();
			for(String arg : args) {
				if((arg.contains("p") || arg.contains("P")) && plugin.isInt(arg.replaceFirst("(?i)p", ""))) page = Integer.parseInt(arg.replaceFirst("(?i)p", "")) - 1;
				else if(plugin.getMaterial(arg, true, player) != null) material = plugin.getMaterial(arg, true, player);
				else seller = arg;
			}
			if(material != null) eList = eList.eq("item", material.getType().toString());
			if(seller != "") eList = eList.ieq("seller", seller);
			DBPageList = query.findPagingList(8);
			//Page number validation
			if(page < 0) {
				sender.sendMessage(ChatColor.RED + "Invalid page number!");
				return true;
			}
			//Get the page worth of info
			DBClass = DBPageList.getPage(page).getList();
			
			if(DBClass.isEmpty()) {
				String message = "";
				if(page > 0) message = "Page " + (page + 1) + " contains no results. Try page 1";
				else if(material != null && seller != "") message = seller + " is not selling any " + material.getType().toString();
				else if(material != null) message = "Nobody is selling any " + material.getType().toString();
				else if(seller != "") message = seller + " is not selling anything";
				else message = "Nobody is selling anything";
				sender.sendMessage(ChatColor.RED + message);
				return true;
			}

			hyphenCount = ((40 - (" Page " + (page + 1) + " ").length()) / 2);
			for(Integer i = 0; i < hyphenCount;) {
				hyphens = hyphens + "-";
				i++;
			}
			if(seller != "" && material != null) sender.sendMessage(ChatColor.GREEN + "Items For Sale, " + material.getType().toString() + " Sold By " + seller + " Only:");
			else if(seller != "") sender.sendMessage(ChatColor.GREEN + "Items For Sale, " + seller + " Only:");
			else if(material != null) sender.sendMessage(ChatColor.GREEN + "Items For Sale, " + material.getType().toString() + " Only:");
			else sender.sendMessage(ChatColor.GREEN + "Items For Sale, No Filter:");
			sender.sendMessage(ChatColor.RED + hyphens + ChatColor.GRAY + " Page " + ChatColor.RED + (page + 1) + " " + hyphens);
			for(Integer i = 0; i < DBClass.size();) {
				DB_ForSale DB = DBClass.get(i);
				String currencyName;
				if(DB.getCost() == 1) currencyName = plugin.economy.currencyNameSingular();
				else currencyName = plugin.economy.currencyNamePlural();
				sender.sendMessage(ChatColor.GOLD + DB.getSeller() + ChatColor.BLUE + ": " + ChatColor.GOLD + DB.getAmount() + " " + DB.getItem() + ChatColor.BLUE + " for " + ChatColor.GOLD + DB.getCost() + " " + currencyName + ChatColor.BLUE + " each.");
				i++;
			}
			if(DBPageList.getPage(page).hasNext()) sender.sendMessage(ChatColor.GREEN + "There is a next page in this list!");
			return true;
		}
		//If you get here, you are the new Chuck Norris!
		return false;
	}

}
