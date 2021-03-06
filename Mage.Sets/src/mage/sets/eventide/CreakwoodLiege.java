/*
 *  Copyright 2010 BetaSteward_at_googlemail.com. All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *     1. Redistributions of source code must retain the above copyright notice, this list of
 *        conditions and the following disclaimer.
 *
 *     2. Redistributions in binary form must reproduce the above copyright notice, this list
 *        of conditions and the following disclaimer in the documentation and/or other materials
 *        provided with the distribution.
 *
 *  THIS SOFTWARE IS PROVIDED BY BetaSteward_at_googlemail.com ``AS IS'' AND ANY EXPRESS OR IMPLIED
 *  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL BetaSteward_at_googlemail.com OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 *  SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 *  ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  The views and conclusions contained in the software and documentation are those of the
 *  authors and should not be interpreted as representing official policies, either expressed
 *  or implied, of BetaSteward_at_googlemail.com.
 */
package mage.sets.eventide;

import java.util.UUID;
import mage.MageInt;
import mage.ObjectColor;
import mage.abilities.common.BeginningOfUpkeepTriggeredAbility;
import mage.abilities.common.SimpleStaticAbility;
import mage.abilities.effects.common.CreateTokenEffect;
import mage.abilities.effects.common.continuous.BoostControlledEffect;
import mage.cards.CardImpl;
import mage.constants.CardType;
import mage.constants.Duration;
import mage.constants.Rarity;
import mage.constants.TargetController;
import mage.constants.Zone;
import mage.filter.common.FilterCreaturePermanent;
import mage.filter.predicate.mageobject.ColorPredicate;
import mage.game.permanent.token.Token;

/**
 *
 * @author anonymous
 */
public class CreakwoodLiege extends CardImpl {

    private static final FilterCreaturePermanent filterBlackCreature = new FilterCreaturePermanent("black creatures");
    private static final FilterCreaturePermanent filterGreenCreature = new FilterCreaturePermanent("green creatures");

    static {
        filterBlackCreature.add(new ColorPredicate(ObjectColor.BLACK));
        filterGreenCreature.add(new ColorPredicate(ObjectColor.GREEN));
    }


    public CreakwoodLiege(UUID ownerId) {
        super(ownerId, 117, "Creakwood Liege", Rarity.RARE, new CardType[]{CardType.CREATURE}, "{1}{B/G}{B/G}{B/G}");
        this.expansionSetCode = "EVE";
        this.subtype.add("Horror");


        this.power = new MageInt(2);
        this.toughness = new MageInt(2);

        // Other black creatures you control get +1/+1.
        this.addAbility(new SimpleStaticAbility(Zone.BATTLEFIELD, new BoostControlledEffect(1, 1, Duration.WhileOnBattlefield, filterBlackCreature, true)));
        // Other green creatures you control get +1/+1.
        this.addAbility(new SimpleStaticAbility(Zone.BATTLEFIELD, new BoostControlledEffect(1, 1, Duration.WhileOnBattlefield, filterGreenCreature, true)));
        // At the beginning of your upkeep, you may put a 1/1 black and green Worm creature token onto the battlefield.
        this.addAbility(new BeginningOfUpkeepTriggeredAbility(new CreateTokenEffect(new CreakwoodLiegeToken(), 1), TargetController.YOU, true));
    }

    public CreakwoodLiege(final CreakwoodLiege card) {
        super(card);
    }

    @Override
    public CreakwoodLiege copy() {
        return new CreakwoodLiege(this);
    }
}

class CreakwoodLiegeToken extends Token {
    CreakwoodLiegeToken() {
        super("Worm", "1/1 black and green Worm creature token");
        cardType.add(CardType.CREATURE);
        color.setBlack(true);
        color.setGreen(true);
        subtype.add("Worm");
        power = new MageInt(1);
        toughness = new MageInt(1);
    }
}

