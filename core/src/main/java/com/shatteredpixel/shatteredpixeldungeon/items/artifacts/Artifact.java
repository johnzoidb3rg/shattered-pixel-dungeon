/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * Shattered Pixel Dungeon
 * Copyright (C) 2014-2024 Evan Debenham
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.shatteredpixel.shatteredpixeldungeon.items.artifacts;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSprite;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;

public class Artifact extends EquipableItem {

	protected Buff passiveBuff;
	protected Buff activeBuff;

	//level is used internally to track upgrades to artifacts, size/logic varies per artifact.
	//already inherited from item superclass
	//exp is used to count progress towards levels for some artifacts
	protected int exp = 0;
	//levelCap is the artifact's maximum level
	protected int levelCap = 0;

	//the current artifact charge
	protected int charge = 0;
	//the build towards next charge, usually rolls over at 1.
	//better to keep charge as an int and use a separate float than casting.
	protected float partialCharge = 0;
	//the maximum charge, varies per artifact, not all artifacts use this.
	protected int chargeCap = 0;

	//used by some artifacts to keep track of duration of effects or cooldowns to use.
	protected int cooldown = 0;

	@Override
	public boolean doEquip( final Hero hero ) {

		if ((hero.belongings.artifact1 != null && hero.belongings.artifact1.getClass() == this.getClass())
				|| (hero.belongings.artifact2 != null && hero.belongings.artifact2.getClass() == this.getClass())
				|| (hero.belongings.artifact3 != null && hero.belongings.artifact3.getClass() == this.getClass())){

			GLog.w( Messages.get(Artifact.class, "cannot_wear_two") );
			return false;

		} else {

			if (hero.belongings.artifact1 != null 
				&& hero.belongings.artifact2 != null 
				&& hero.belongings.artifact3 != null) {

				final Artifact[] miscs = new Artifact[3];
				miscs[0] = hero.belongings.artifact1;
				miscs[1] = hero.belongings.artifact2;
				miscs[2] = hero.belongings.artifact3;

				final boolean[] enabled = new boolean[3];
				enabled[0] = miscs[0] != null;
				enabled[1] = miscs[1] != null;
				enabled[2] = miscs[2] != null;

				GameScene.show(
						new WndOptions(new ItemSprite(this),
								Messages.get(Artifact.class, "unequip_title"),
								Messages.get(Artifact.class, "unequip_message"),
								miscs[0] == null ? "---" : Messages.titleCase(miscs[0].title()),
								miscs[1] == null ? "---" : Messages.titleCase(miscs[1].title()),
								miscs[2] == null ? "---" : Messages.titleCase(miscs[2].title())) {

							@Override
							protected void onSelect(int index) {

								Artifact equipped = miscs[index];
								//we directly remove the item because we want to have inventory capacity
								// to unequip the equipped one, but don't want to trigger any other
								// item detaching logic
								int slot = Dungeon.quickslot.getSlot(Artifact.this);
								slotOfUnequipped = -1;
								Dungeon.hero.belongings.backpack.items.remove(Artifact.this);
								if (equipped.doUnequip(hero, true, false)) {
									Dungeon.hero.belongings.backpack.items.add(Artifact.this);
									doEquip(hero);
								} else {
									Dungeon.hero.belongings.backpack.items.add(Artifact.this);
								}
								if (slot != -1) {
									Dungeon.quickslot.setSlot(slot, Artifact.this);
								} else if (slotOfUnequipped != -1 && defaultAction() != null){
									Dungeon.quickslot.setSlot(slotOfUnequipped, Artifact.this);
								}
								updateQuickslot();
							}

							@Override
							protected boolean enabled(int index) {
								return enabled[index];
							}
						});

				return false;

			} else {

				if (hero.belongings.artifact1 == null)      hero.belongings.artifact1 = (Artifact) this;
				else if (hero.belongings.artifact2 == null) hero.belongings.artifact2 = (Artifact) this;
				else                                        hero.belongings.artifact3 = (Artifact) this;

				detach( hero.belongings.backpack );

				Talent.onItemEquipped(hero, this);
				activate( hero );

				cursedKnown = true;
				if (cursed) {
					equipCursed( hero );
					GLog.n( Messages.get(this, "equip_cursed", this) );
				}

				hero.spendAndNext( timeToEquip(hero) );

				identify();

				return true;

			}
		}

	}

	public void activate( Char ch ) {
		if (passiveBuff != null){
			if (passiveBuff.target != null) passiveBuff.detach();
			passiveBuff = null;
		}
		passiveBuff = passiveBuff();
		passiveBuff.attachTo(ch);
	}

	@Override
	public boolean doUnequip( Hero hero, boolean collect, boolean single ) {
		if (super.doUnequip( hero, collect, single )) {

			if (hero.belongings.artifact1 == this) {
				hero.belongings.artifact1 = null;
			} else if (hero.belongings.artifact2 == this) {
				hero.belongings.artifact2 = null;
			} else if (hero.belongings.artifact3 == this){
				hero.belongings.artifact3 = null;
			}

			if (passiveBuff != null) {
				if (passiveBuff.target != null) passiveBuff.detach();
				passiveBuff = null;
			}

			return true;

		} else {

			return false;

		}
	}

	@Override
	public boolean isEquipped( Hero hero ) {
		return hero != null && (hero.belongings.artifact1() == this
				|| hero.belongings.artifact2() == this
				|| hero.belongings.artifact3() == this);
	}

	@Override
	public boolean isUpgradable() {
		return false;
	}

	@Override
	public int visiblyUpgraded() {
		return levelKnown ? Math.round((level()*10)/(float)levelCap): 0;
	}

	@Override
	public int buffedVisiblyUpgraded() {
		return visiblyUpgraded();
	}

	@Override
	public int buffedLvl() {
		//level isn't affected by buffs/debuffs
		return level();
	}

	//transfers upgrades from another artifact, transfer level will equal the displayed level
	public void transferUpgrade(int transferLvl) {
		upgrade(Math.round((transferLvl*levelCap)/10f));
	}

	@Override
	public String info() {
		if (cursed && cursedKnown && !isEquipped( Dungeon.hero )) {
			return super.info() + "\n\n" + Messages.get(Artifact.class, "curse_known");
			
		} else if (!isIdentified() && cursedKnown && !isEquipped( Dungeon.hero)) {
			return super.info() + "\n\n" + Messages.get(Artifact.class, "not_cursed");
			
		} else {
			return super.info();
			
		}
	}

	@Override
	public String status() {
		
		//if the artifact isn't IDed, or is cursed, don't display anything
		if (!isIdentified() || cursed){
			return null;
		}

		//display the current cooldown
		if (cooldown != 0)
			return Messages.format( "%d", cooldown );

		//display as percent
		if (chargeCap == 100)
			return Messages.format( "%d%%", charge );

		//display as #/#
		if (chargeCap > 0)
			return Messages.format( "%d/%d", charge, chargeCap );

		//if there's no cap -
		//- but there is charge anyway, display that charge
		if (charge != 0)
			return Messages.format( "%d", charge );

		//otherwise, if there's no charge, return null.
		return null;
	}

	@Override
	public Item random() {
		//always +0
		
		//30% chance to be cursed
		if (Random.Float() < 0.3f) {
			cursed = true;
		}
		return this;
	}

	@Override
	public int value() {
		int price = 100;
		if (level() > 0)
			price += 20*visiblyUpgraded();
		if (cursed && cursedKnown) {
			price /= 2;
		}
		if (price < 1) {
			price = 1;
		}
		return price;
	}


	protected ArtifactBuff passiveBuff() {
		return null;
	}

	protected ArtifactBuff activeBuff() {return null; }
	
	public void charge(Hero target, float amount){
		//do nothing by default;
	}

	public class ArtifactBuff extends Buff {

		@Override
		public boolean attachTo( Char target ) {
			if (super.attachTo( target )) {
				//if we're loading in and the hero has partially spent a turn, delay for 1 turn
				if (target instanceof Hero && Dungeon.hero == null && cooldown() == 0 && target.cooldown() > 0) {
					spend(TICK);
				}
				return true;
			}
			return false;
		}

		public int itemLevel() {
			return level();
		}

		public boolean isCursed() {
			return target.buff(MagicImmune.class) == null && cursed;
		}

		public void charge(Hero target, float amount){
			Artifact.this.charge(target, amount);
		}

	}
	
	private static final String EXP = "exp";
	private static final String CHARGE = "charge";
	private static final String PARTIALCHARGE = "partialcharge";

	@Override
	public void storeInBundle( Bundle bundle ) {
		super.storeInBundle(bundle);
		bundle.put( EXP , exp );
		bundle.put( CHARGE , charge );
		bundle.put( PARTIALCHARGE , partialCharge );
	}

	@Override
	public void restoreFromBundle( Bundle bundle ) {
		super.restoreFromBundle(bundle);
		exp = bundle.getInt( EXP );
		if (chargeCap > 0)  charge = Math.min( chargeCap, bundle.getInt( CHARGE ));
		else                charge = bundle.getInt( CHARGE );
		partialCharge = bundle.getFloat( PARTIALCHARGE );
	}
}
