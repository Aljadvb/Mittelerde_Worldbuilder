package io.github.aljadvb.worldbuilder.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aljadvb.worldbuilder.R
import io.github.aljadvb.worldbuilder.model.Attribute
import io.github.aljadvb.worldbuilder.model.Outcome
import io.github.aljadvb.worldbuilder.model.Token
import io.github.aljadvb.worldbuilder.model.TokenType

@Composable
fun tokenTypeLabel(token: Token): String = when (token.type) {
    TokenType.SEARCH -> stringResource(R.string.token_search)
    TokenType.PERSON -> stringResource(R.string.token_person)
    TokenType.THREAT -> stringResource(R.string.token_threat)
    TokenType.EXPLORE -> stringResource(R.string.token_explore)
    TokenType.CUSTOM -> token.customType.orEmpty()
}

@Composable
fun attributeLabel(attribute: Attribute): String = stringResource(
    when (attribute) {
        Attribute.MIGHT -> R.string.attribute_might
        Attribute.AGILITY -> R.string.attribute_agility
        Attribute.WISDOM -> R.string.attribute_wisdom
        Attribute.SPIRIT -> R.string.attribute_spirit
        Attribute.WIT -> R.string.attribute_wit
    },
)

@Composable
fun outcomeLabel(outcome: Outcome): String = stringResource(
    when (outcome) {
        Outcome.VICTORY -> R.string.resolution_victory
        Outcome.DEFEAT -> R.string.resolution_defeat
        Outcome.NEUTRAL -> R.string.resolution_neutral
    },
)
