# 🎮 Fun Friday Game Ideas

> **Volatile, Simple, Engaging**

## Architecture Overview

Each game follows the existing architecture:
- **GameConfig**: Game-specific settings
- **GameRound**: Server-side game state
- **GameRoundInfo**: Client-facing DTO
- **GameStrategy**: Game logic implementation

---

## Game 1: Emoji Story Chain

### Concept
Players take turns adding emojis to build a collaborative story. Each player adds 1-3 emojis, and at the end, everyone votes on the best "story moment."

### Gameplay Flow
1. Host starts the game with a theme (e.g., "Adventure", "Romance", "Horror")
2. First player adds 1-3 emojis to start the story
3. Next player sees previous emojis and adds their own 1-3 emojis
4. Chain continues until all players have contributed
5. Final emoji story is displayed
6. Everyone votes for the funniest/best contribution (can't vote for self)
7. Points awarded based on votes received

### Example Gameplay

**Theme: "A Day Gone Wrong"**

| Player | Emojis | Interpretation |
|--------|--------|----------------|
| Alice starts | ☀️😊🚗 | Sunny day, happy, driving to work |
| Bob adds | ☕💨😱 | Coffee spills, steam, shock! |
| Charlie adds | 👔💦😤 | Shirt stained, wet, frustrated |
| Diana adds | 🏢👀😅 | Arrives at office, everyone stares, embarrassed laugh |

**Final Story:** ☀️😊🚗 → ☕💨😱 → 👔💦😤 → 🏢👀😅

**Voting Results:**
- Bob: 2 votes = 100 points + 25 bonus (most votes) = **125 points**
- Diana: 1 vote = 50 points
- Charlie: 1 vote = 50 points
- Alice: 0 votes = 0 points

### Round Structure
- Each player gets 20 seconds to add their emojis
- Round ends when all players have contributed
- Voting phase: 15 seconds

### Scoring
| Action | Points |
|--------|--------|
| Per vote received | 50 pts |
| Most votes bonus | +25 pts |

### Config (EmojiStoryConfig)
```typescript
{
  theme: string,              // optional theme for the round
  maxEmojisPerTurn: number,   // 1-5, default 3
  turnDuration: number,       // seconds per turn, default 20
  votingDuration: number      // seconds for voting, default 15
}
```

### Round Info (EmojiStoryRoundInfo)
```typescript
{
  currentPlayerIndex: number,
  storyChain: { playerName: string, emojis: string }[],
  phase: 'ADDING' | 'VOTING' | 'RESULTS',
  votes: { [voterName: string]: string },
  theme: string
}
```

### Theme Ideas
- "A Day Gone Wrong"
- "Epic Adventure"
- "Love Story"
- "Horror Night"
- "Office Drama"
- "Weekend Plans"
- "Cooking Disaster"
- "Pet Chaos"
- "Travel Mishap"
- "Monday Morning"

### Technical Notes
- Use emoji picker on frontend
- WebSocket message types: `/app/addEmojis`, `/app/voteEmoji`
- No database needed - story exists only during the round

---

## Game 2: Speed Typing Race ✅ IMPLEMENTED

### Concept
All players race to type a displayed sentence/phrase as fast and accurately as possible. First to finish correctly wins, but typos cost time penalties.

### Gameplay Flow
1. Server generates a random sentence (from predefined list or algorithm)
2. Countdown 3-2-1, sentence appears for everyone simultaneously
3. Players type as fast as they can
4. Real-time progress shown (who's at what percentage)
5. First to complete with 95%+ accuracy wins
6. Round ends when winner found OR time runs out

### Example Gameplay

**Round 1 - Difficulty: MEDIUM**

**Target Text:** "The quick brown fox jumps over the lazy dog near the park."

**Live Progress (5 seconds in):**
| Player | Progress | Accuracy |
|--------|----------|----------|
| Alice | ████████████░░░░░░░░ 58% | 100% |
| Bob | ██████████░░░░░░░░░░ 48% | 97% |
| Charlie | ███████░░░░░░░░░░░░░ 35% | 100% |
| Diana | █████████████░░░░░░░ 65% | 98% |

**Final Results (Target: 58 chars, Round: 45s):**
| Rank | Player | Time | Accuracy | Points |
|------|--------|------|----------|--------|
| 🏆 1st | Alice | 11.2s | 100% | 116 + 10 bonus + 34 time = **160 pts** |
| 🥈 2nd | Charlie | 14.1s | 100% | 116 + 10 bonus + 31 time = **157 pts** |
| 🥉 3rd | Diana | 9.8s | 98% | 113 + 0 bonus + 35 time = **148 pts** |
| 4th | Bob | 12.5s | 95% | 107 + 0 bonus + 32 time = **139 pts** |

### Round Structure
- Difficulty levels: Easy (short phrases), Medium (sentences), Hard (paragraphs)
- Time limit: 30-60 seconds depending on difficulty
- Multiple rounds with increasing difficulty

### Scoring
| Action | Points |
|--------|--------|
| Each correct character | +2 pts |
| Each incorrect character | -1 pt |
| Each extra/missing character | -1 pt |
| 100% accuracy bonus | +10 pts |
| Time bonus (per remaining second) | +1 pt |

### Config (SpeedTypingConfig)
```typescript
{
  difficulty: 'EASY' | 'MEDIUM' | 'HARD',
  roundDuration: number,       // seconds
  minAccuracy: number          // percentage required to win, default 95
}
```

### Round Info (SpeedTypingRoundInfo)
```typescript
{
  targetText: string,
  playerProgress: { 
    [playerName: string]: { 
      percentage: number, 
      accuracy: number, 
      finished: boolean, 
      finishTime: number 
    } 
  },
  startTime: number,
  rankings: string[]  // ordered list of finishers
}
```

### Sentence Sources (hardcoded lists)

**EASY (10-20 characters):**
- "Hello world!"
- "Fun Friday rocks!"
- "Type faster now."
- "Code is poetry."
- "Happy coding day!"
- "Debug this code."
- "Ship it today!"
- "Git push origin."

**MEDIUM (40-70 characters):**
- "The quick brown fox jumps over the lazy dog near the park."
- "Pack my box with five dozen liquor jugs for the party."
- "How vexingly quick daft zebras jump over the wooden fence!"
- "A wizard's job is to vex chumps quickly in fog and rain."
- "Sphinx of black quartz, judge my vow to code better today."

**HARD (100+ characters):**
- "Programming is not about typing, it's about thinking. But fast typing helps during Fun Friday games!"
- "The best error message is the one that never shows up. The second best has a helpful stack trace."
- "In software development, premature optimization is the root of all evil, but so is premature abstraction."

### Technical Notes
- Real-time progress updates via WebSocket
- Calculate accuracy using Levenshtein distance
- Frontend shows live typing with correct/incorrect highlighting

---

## Game 3: Quick Draw Battle

### Concept
Two players face off in a drawing duel. Both get the same word, draw simultaneously, and other players vote on whose drawing is better.

### Gameplay Flow
1. Two players randomly selected as "duelists"
2. Both receive the same secret word to draw
3. 45-second simultaneous drawing phase
4. Both canvases revealed side-by-side
5. Other players vote for the better drawing (can't see who drew which)
6. Winner revealed with vote counts

### Example Gameplay

**Duel 1: Alice vs Bob**  
**Secret Word: "ROCKET" 🚀**

**Drawing Phase (45 seconds):**
- Duelists see their word and draw on their canvas
- Other players wait

**Voting Phase (Anonymous):**
| Drawing A | Drawing B |
|-----------|-----------|
| Simple rocket sketch | Detailed rocket with window |

Voters: Charlie, Diana, Eve

**Votes:**
- Charlie voted: A
- Diana voted: B
- Eve voted: A

**Reveal:**
- Drawing A = Alice (2 votes) ← **WINNER! 🏆**
- Drawing B = Bob (1 vote)

**Points Awarded:**
| Player | Points | Reason |
|--------|--------|--------|
| Alice | 100 pts | Duel winner |
| Bob | 25 pts | Participation |
| Charlie | 10 pts | Voted with majority |
| Diana | 0 pts | Voted against majority |
| Eve | 10 pts | Voted with majority |

### Round Structure
- Each round is one duel
- Rotate through players so everyone duels
- Final scores tallied after all duels

### Scoring
| Action | Points |
|--------|--------|
| Winner of duel | 100 pts |
| Loser of duel | 25 pts |
| Tie | 50 pts each |
| Voting with majority | 10 pts |

### Config (QuickDrawBattleConfig)
```typescript
{
  drawingDuration: number,     // seconds, default 45
  votingDuration: number,      // seconds, default 20
  wordDifficulty: 'EASY' | 'MEDIUM' | 'HARD'
}
```

### Round Info (QuickDrawBattleRoundInfo)
```typescript
{
  duelist1: string,
  duelist2: string,
  secretWord: string,
  phase: 'DRAWING' | 'VOTING' | 'RESULTS',
  drawing1: DrawingStroke[],
  drawing2: DrawingStroke[],
  votes: { [voterName: string]: 1 | 2 },
  winner: string | null
}
```

### Word Lists (hardcoded)

**EASY (simple objects):**
cat, dog, sun, moon, star, tree, house, car, fish, bird, flower, apple, book, clock, hat, shoe, ball, cup, key, boat, plane, train, pizza, ice cream

**MEDIUM (more detail needed):**
bicycle, elephant, rocket, guitar, umbrella, volcano, dinosaur, helicopter, snowman, lighthouse, windmill, castle, mermaid, unicorn, dragon, pirate, ninja, wizard

**HARD (abstract or complex):**
democracy, gravity, jealousy, confusion, time, music, freedom, happiness, anger, love, dream, silence, internet, algorithm, recursion, debugging

### Technical Notes
- Reuse existing drawing canvas component
- Anonymous voting - shuffle which drawing is shown as "A" vs "B"
- Both drawings happen simultaneously (split screen for duelists)

---

## Game 4: Reaction Showdown ✅ IMPLEMENTED

### Concept
Test players' reaction time! A signal appears after a random delay, and players must tap/click as fast as possible. Watch out for fake-outs! Standard round-based game like others - one reaction opportunity per round.

### Gameplay Flow
1. Round starts, screen shows "Wait for it..." (yellow background)
2. Random delay (2-5 seconds)
3. Screen changes to "TAP NOW!" (green) or "FAKE OUT!" (red)
4. Players tap/click as fast as possible
5. Round timer ends the round (standard timer like other games)
6. Points awarded based on reaction time and penalties
7. Scores shown in sidebar like other games

### Example Gameplay

**Round 1 of 3**

**Phase 1 - WAITING:**
```
┌────────────────────────────────┐
│                                │
│       Wait for it...           │  ← YELLOW BACKGROUND
│                                │
└────────────────────────────────┘
(DON'T tap yet! Wait for the signal...)
```

⚠️ Bob tapped early! FALSE START - Penalty applied!

**Phase 2 - GO! (after random delay):**
```
┌────────────────────────────────┐
│                                │
│       T A P   N O W !          │  ← GREEN BACKGROUND
│                                │
└────────────────────────────────┘
```

**Round Results (after timer ends):**
| Player | Time | Points | Breakdown |
|--------|------|--------|-----------|
| 🏆 Alice | 187ms | 181 pts | 100 base + 31 speed bonus + 50 fastest bonus |
| Charlie | 234ms | 126 pts | 100 base + 26 speed bonus |
| Diana | 312ms | 118 pts | 100 base + 18 speed bonus |
| Eve | - | 0 pts | Didn't tap |
| ❌ Bob | FALSE START | -50 pts | False start penalty |

### Fake-Out Example

**Round 2 of 3 (FAKE-OUT!)**

**Phase 2 - FAKE-OUT!:**
```
┌────────────────────────────────┐
│                                │
│       FAKE OUT!                │  ← RED BACKGROUND
│                                │
└────────────────────────────────┘
(It's a trap! Don't tap!)
```

- ❌ Charlie tapped on fake-out! -25 points
- ❌ Eve tapped on fake-out! -25 points
- ✓ Alice, Bob, Diana didn't tap - no penalty!

### Round Structure
- One reaction opportunity per round (standard round-based like other games)
- Some rounds have "fake-outs" (screen shows "FAKE OUT!" instead of "TAP NOW!")
- Round timer ends the round, scores shown in sidebar like other games

### Scoring
| Action | Points |
|--------|--------|
| Base (valid reaction) | 100 pts |
| Speed bonus | +1 pt per 10ms faster than 500ms baseline |
| Fastest player bonus | +50 pts |
| False start penalty | -50 pts |
| Fake-out tap penalty | -25 pts |
| Didn't tap | 0 pts |

### Config (ReactionShowdownConfig)
```typescript
{
  rounds: number,              // default 3
  roundDuration: number,       // seconds, default 10
  minDelay: number,            // milliseconds, default 2000
  maxDelay: number,            // milliseconds, default 5000
  includeFakeOuts: boolean,    // default true
  fakeOutChance: number        // percentage, default 20
}
```

### Round Info (ReactionShowdownRoundInfo)
```typescript
{
  phase: 'WAITING' | 'REACT' | 'FAKEOUT',
  isFakeOut: boolean,
  signalTime: number,
  playerReactions: { [playerName: string]: ReactionInfo },
  fastestPlayer: string | null,
  fastestTime: number
}

interface ReactionInfo {
  reactionTime: number,   // milliseconds, -1 if didn't tap
  falseStart: boolean,
  tappedFakeOut: boolean
}
```

### Technical Notes
- Server controls timing to prevent cheating
- Signal triggered automatically when round starts (after random delay)
- Standard round timer ends the round
- Scores displayed in sidebar like other games

---

## Game 5: Word Association Chain

### Concept
Players take turns saying words that associate with the previous word. Break the chain (repeat a word, take too long, or say something unrelated) and you're out! Last player standing wins.

### Gameplay Flow
1. Server provides starting word (e.g., "Ocean")
2. First player must type a related word (e.g., "Wave")
3. Next player associates with "Wave" (e.g., "Beach")
4. Continue around the circle
5. Player is OUT if they:
   - Take more than 5 seconds
   - Repeat any word already used
   - Other players vote "unrelated" (majority)
6. Last player standing wins!

### Example Gameplay

**Round 1 - Starting Word: "OCEAN"**  
**Players: Alice, Bob, Charlie, Diana, Eve (5 players)**

**Word Chain Progress:**
```
[OCEAN] → Alice: "Wave" (1.2s) ✓
        → Bob: "Beach" (0.8s) ✓
        → Charlie: "Sand" (1.5s) ✓
        → Diana: "Castle" (2.1s) ✓
        → Eve: "King" (1.9s) ✓
        → Alice: "Crown" (1.1s) ✓
        → Bob: "Gold" (0.7s) ✓
        → Charlie: "Ring" (2.3s) ✓
        → Diana: "Wedding" (1.8s) ✓
        → Eve: "Beach" ❌ REPEATED! (already used by Bob)
```

💀 **EVE IS ELIMINATED!** Reason: Repeated word "Beach"

**Challenge Example:**
```
Diana: "Airplane" (2.8s) 🤔 CHALLENGED by Bob!

CHALLENGE VOTE: Is "Airplane" related to "Model"?
- Alice votes: ✓ RELATED (model airplane makes sense!)
- Bob votes: ✗ UNRELATED
- Charlie votes: ✓ RELATED

Result: 2-1 in favor of RELATED - Diana survives!
```

**Final Results:**
| Rank | Player | Points | Answers Given |
|------|--------|--------|---------------|
| 🏆 1st | Alice | 100 pts | 8 answers |
| 🥈 2nd | Diana | 75 pts | 4 answers |
| 🥉 3rd | Charlie | 50 pts | 3 answers |
| 4th | Bob | 25 pts | 3 answers (timeout) |
| 5th | Eve | 25 pts | 3 answers (repeated) |

### Round Structure
- Elimination style within a round
- Round ends when one player remains
- Multiple rounds, points accumulate

### Scoring
| Placement | Points |
|-----------|--------|
| Last standing | 100 pts |
| 2nd to last | 75 pts |
| 3rd to last | 50 pts |
| Others | 25 pts |

### Config (WordAssociationConfig)
```typescript
{
  turnDuration: number,        // seconds, default 5
  enableVoting: boolean,       // allow players to challenge, default true
  startingWords: string[]      // list to pick from randomly
}
```

### Round Info (WordAssociationRoundInfo)
```typescript
{
  wordChain: { playerName: string, word: string, timestamp: number }[],
  currentPlayerIndex: number,
  activePlayers: string[],
  eliminatedPlayers: { name: string, reason: string }[],
  phase: 'PLAYING' | 'CHALLENGING' | 'RESULTS',
  currentChallenge: { 
    challenger: string, 
    votes: { [name: string]: 'related' | 'unrelated' } 
  } | null
}
```

### Starting Words (hardcoded)
Ocean, Fire, Music, Night, Dream, Space, Garden, Thunder, Crystal, Shadow, Mountain, River, Dance, Storm, Magic, Winter, Summer, Forest, Desert, City, Money, Time, Power, Love, Food, Sport, Animal, Color, Movie, Book

### Technical Notes
- Case-insensitive word matching
- Trim whitespace
- Could add basic word validation (is it a real English word?) using simple dictionary
- Challenge system: if word seems unrelated, any player can challenge

---

## Game 6: Trivia Blitz

### Concept
Rapid-fire trivia questions! Questions appear one at a time, all players answer simultaneously. Faster correct answers = more points!

### Gameplay Flow
1. Question appears with 4 multiple choice options
2. All players select their answer (10-15 seconds)
3. Correct answer revealed
4. Points awarded based on correctness AND speed
5. Next question immediately
6. 10 questions per round

### Example Gameplay

**Question 3 of 10 - Category: SCIENCE**

> What is the chemical symbol for gold?
> - A) Ag
> - B) Fe
> - C) Au ✓
> - D) Cu

⏱️ 8.3 seconds remaining

**Players answered:**
- ✓ Alice answered (1.8s)
- ✓ Bob answered (2.4s)
- ✓ Charlie answered (5.1s)
- ⏳ Diana thinking...
- ✓ Eve answered (3.2s)

**Results Reveal:**

Correct Answer: **C) Au** (Gold - from Latin "Aurum")

| Player | Answer | Time | Streak | Points Earned |
|--------|--------|------|--------|---------------|
| Alice | C) ✓ | 1.8s | 🔥x3 | 100 + 30 streak = **130 pts** |
| Bob | C) ✓ | 2.4s | x1 | 75 pts (fast) |
| Eve | C) ✓ | 3.2s | x2 | 50 + 20 streak = **70 pts** |
| Charlie | C) ✓ | 5.1s | x1 | 25 pts (slow but correct) |
| Diana | B) ✗ | 8.9s | 💔x0 | 0 pts (streak broken!) |

**Round Summary:**
| Rank | Player | Points | Correct | Best Streak |
|------|--------|--------|---------|-------------|
| 🏆 | Alice | 785 pts | 9/10 | 5 |
| 🥈 | Eve | 620 pts | 8/10 | 4 |
| 🥉 | Bob | 545 pts | 7/10 | 3 |
| 4 | Charlie | 410 pts | 8/10 | 2 |
| 5 | Diana | 285 pts | 5/10 | 2 |

🔥 **LONGEST STREAK:** Alice with 5 correct in a row!  
⚡ **FASTEST ANSWER:** Bob on Q7 (0.9 seconds!)

### Round Structure
- 10 questions per round
- Categories rotate or are random
- Progressive difficulty optional

### Scoring
| Speed Tier | Points |
|------------|--------|
| Correct + fastest | 100 pts |
| Correct + fast (top 25%) | 75 pts |
| Correct + medium (25-50%) | 50 pts |
| Correct + slow (bottom 50%) | 25 pts |
| Incorrect | 0 pts |
| Streak bonus | +10 pts per consecutive correct |

### Config (TriviaBlitzConfig)
```typescript
{
  questionsPerRound: number,   // default 10
  questionDuration: number,    // seconds, default 12
  categories: string[],        // optional filter
  difficulty: 'EASY' | 'MEDIUM' | 'HARD' | 'MIXED'
}
```

### Round Info (TriviaBlitzRoundInfo)
```typescript
{
  currentQuestionIndex: number,
  currentQuestion: { 
    question: string, 
    options: string[], 
    category: string 
  },
  playerAnswers: { 
    [playerName: string]: { answer: number, timestamp: number } 
  },
  correctAnswer: number | null,
  questionResults: { 
    questionIndex: number, 
    correct: string[], 
    incorrect: string[] 
  }[],
  streaks: { [playerName: string]: number }
}
```

### Trivia Categories & Questions (hardcoded samples)

**GENERAL KNOWLEDGE:**
- Q: What is the capital of Australia? → C) Canberra
- Q: How many continents are there on Earth? → C) 7
- Q: What is the largest ocean on Earth? → D) Pacific

**MOVIES & TV:**
- Q: Who directed "Jurassic Park"? → B) Steven Spielberg
- Q: What year did the first "Star Wars" movie release? → C) 1977
- Q: Which TV show features a character named Walter White? → B) Breaking Bad

**SCIENCE:**
- Q: What is the chemical symbol for gold? → C) Au
- Q: How many bones are in the adult human body? → B) 206
- Q: What planet is known as the Red Planet? → C) Mars

**TECHNOLOGY:**
- Q: What year was the first iPhone released? → C) 2007
- Q: What does "HTML" stand for? → B) HyperText Markup Language
- Q: Who founded Microsoft? → B) Bill Gates

**GEOGRAPHY:**
- Q: What is the smallest country in the world? → B) Vatican City
- Q: Which river is the longest in the world? → B) Nile
- Q: What is the largest desert in the world? → C) Antarctic

**SPORTS:**
- Q: How many players are on a standard soccer team on the field? → C) 11
- Q: In which sport would you perform a "slam dunk"? → B) Basketball
- Q: How many holes are there in a full round of golf? → B) 18

*(Add 50-100 questions per category for variety)*

### Technical Notes
- Shuffle question order and answer order each time
- Track answer timestamps for speed-based scoring
- Could expand to use external trivia API later, but start with hardcoded

---

## Game 7: Imposter Word

### Concept
Everyone gets the same secret word EXCEPT one "imposter" who gets a different (but related) word. Players describe their word, try to find the imposter, while the imposter tries to blend in!

### Gameplay Flow
1. One random player is secretly the "Imposter"
2. Regular players see: "Your word is: PIZZA"
3. Imposter sees: "Your word is: PASTA" (related but different)
4. Each player gives a one-word clue about their word
5. Discussion phase (30 seconds)
6. Everyone votes who they think is the Imposter
7. Reveal: Was the Imposter caught?

### Example Gameplay

**Round 1 - Secret Assignment**

| Player | Screen | Word |
|--------|--------|------|
| Alice | Normal | 🍕 PIZZA |
| Bob | ⚠️ IMPOSTER | 🍝 PASTA |
| Charlie | Normal | 🍕 PIZZA |
| Diana | Normal | 🍕 PIZZA |
| Eve | Normal | 🍕 PIZZA |

**Clue-Giving Phase (random order, 10 seconds each):**
- Charlie's turn: "Cheese" ✓ *(Safe clue - both pizza and pasta can have cheese)*
- Alice's turn: "Pepperoni" ✓ *(Specific to pizza - could expose imposter!)*
- Bob's turn (IMPOSTER): "Italian" ✓ *(Smart! Both pizza and pasta are Italian)*
- Eve's turn: "Slice" ✓ *(Specific to pizza - you don't slice pasta!)*
- Diana's turn: "Delivery" ✓ *(Common for pizza - risky clue!)*

**Discussion Phase (30 seconds):**
> Alice: "Hmm, 'Italian' is pretty vague..."  
> Charlie: "Yeah but 'Delivery' could be anything too"  
> Eve: "Bob's clue was safe but not specific..."  
> Diana: "I said delivery because pizza delivery! That's specific!"  
> Bob: "Italian food is obviously what we're talking about 🤷"

**Voting Phase:**
- Alice → Bob
- Bob → Diana (trying to deflect!)
- Charlie → Bob
- Diana → Bob
- Eve → Diana

**The Reveal:**
```
Vote Results: Bob (3 votes) | Diana (2 votes)

The group voted for: BOB

🎭 BOB WAS THE IMPOSTER! 🎭

Real Word: PIZZA 🍕
Imposter Word: PASTA 🍝

THE CREW WINS!
```

**Bonus Round - Imposter's Last Chance:**
> Bob (Imposter): Can you guess the REAL word to earn 50 points?  
> Bob guesses: "PIZZA" ✓  
> Bob earns 50 bonus points for guessing the real word!

**Round Points:**
| Player | Points | Reason |
|--------|--------|--------|
| Alice | +50 pts | Correctly identified imposter |
| Charlie | +50 pts | Correctly identified imposter |
| Diana | +50 pts | Correctly identified imposter |
| Eve | +0 pts | Voted wrong |
| Bob (Imposter) | +50 pts | Caught but guessed word |

### Imposter Wins Example
```
Vote Results: Diana (3 votes) | Bob (2 votes)

The group voted for: DIANA

😱 DIANA WAS INNOCENT!

🎭 BOB WAS THE IMPOSTER AND ESCAPED! 🎭

Bob earns 100 points for surviving!
Everyone else earns 0 points.
```

### Round Structure
- Each round has one imposter
- Rotate who is imposter
- 3-5 rounds per game

### Scoring
| Action | Points |
|--------|--------|
| Correctly identifying Imposter | 50 pts |
| Imposter survives (not caught) | 100 pts |
| Imposter caught | 0 pts |
| Imposter guesses real word (bonus) | 50 pts |

### Config (ImposterWordConfig)
```typescript
{
  clueGivingDuration: number,  // seconds per clue, default 10
  discussionDuration: number,  // seconds, default 30
  votingDuration: number,      // seconds, default 15
  imposterGuessesWord: boolean // bonus round feature, default true
}
```

### Round Info (ImposterWordRoundInfo)
```typescript
{
  realWord: string,
  imposterWord: string,
  imposterName: string,
  clues: { playerName: string, clue: string }[],
  phase: 'CLUE_GIVING' | 'DISCUSSION' | 'VOTING' | 'IMPOSTER_GUESS' | 'RESULTS',
  votes: { [voterName: string]: string },
  imposterWordGuess: string | null
}
```

### Word Pairs (real word / imposter word)

**FOOD:**
Pizza/Pasta, Burger/Hot Dog, Sushi/Ramen, Cake/Cookie, Coffee/Tea, Ice Cream/Frozen Yogurt, Tacos/Burritos, Steak/Chicken

**PLACES:**
Beach/Pool, Mountain/Hill, Library/Bookstore, Restaurant/Cafe, Hotel/Motel, Airport/Train Station, Museum/Art Gallery, Zoo/Aquarium

**ACTIVITIES:**
Running/Walking, Swimming/Diving, Singing/Dancing, Reading/Writing, Cooking/Baking, Painting/Drawing, Hiking/Camping, Fishing/Hunting

**ENTERTAINMENT:**
Movie/TV Show, Concert/Festival, Video Game/Board Game, Book/Magazine, Podcast/Radio, Theater/Opera, Circus/Carnival, Comedy/Drama

**ANIMALS:**
Dog/Cat, Lion/Tiger, Horse/Donkey, Dolphin/Whale, Eagle/Hawk, Snake/Lizard, Butterfly/Moth, Bee/Wasp

### Technical Notes
- Server randomly assigns imposter role
- Imposter's word is never shown to others until end
- Clues are given in random order to prevent position bias
- Voting is simultaneous and hidden until reveal

---

## Game 8: Memory Match Race

### Concept
Classic memory card game but competitive! All players share the same board. Take turns flipping cards - if you match, you score and go again. If not, cards flip back and next player goes.

### Gameplay Flow
1. Board of face-down cards appears (4x4 = 8 pairs, or 6x6 = 18 pairs)
2. Players take turns (random order)
3. Current player clicks two cards to flip
4. Match = keep the pair, take another turn
5. No match = cards flip back after 2 seconds, next player's turn
6. Game ends when all pairs found

### Example Gameplay

**Initial Board (4x4 Grid - 8 pairs):**
```
      Col 1    Col 2    Col 3    Col 4
    ┌───────┬───────┬───────┬───────┐
Row 1 │  ❓   │  ❓   │  ❓   │  ❓   │
    ├───────┼───────┼───────┼───────┤
Row 2 │  ❓   │  ❓   │  ❓   │  ❓   │
    ├───────┼───────┼───────┼───────┤
Row 3 │  ❓   │  ❓   │  ❓   │  ❓   │
    ├───────┼───────┼───────┼───────┤
Row 4 │  ❓   │  ❓   │  ❓   │  ❓   │
    └───────┴───────┴───────┴───────┘

Hidden pairs: 🍎🍎 🍊🍊 🍋🍋 🍇🍇 🍓🍓 🍒🍒 🥝🥝 🍑🍑
```

**Alice's Turn:**
- Alice clicks [1,1] and [2,3]: 🍎 ≠ 🍋 - NO MATCH!
- Cards flip back after 2 seconds
- Next: Bob

**Bob's Turn:**
- Bob remembers Alice's picks! Clicks [1,1] and [3,2]: 🍎 = 🍎 - **MATCH! ✨**
- Bob scores +25 points! Cards stay revealed. Bob gets another turn!

**Final Results:**
| Rank | Player | Pairs | Points | Bonus |
|------|--------|-------|--------|-------|
| 🏆 | Diana | 3 | 75 pts | +25 last pair + 10 streak = **110 pts** |
| 🥈 | Charlie | 3 | 75 pts | +10 streak = **85 pts** |
| 🥉 | Bob | 2 | 50 pts | **50 pts** |
| 4 | Alice | 2 | 50 pts | **50 pts** |

🔥 **HOT STREAK:** Charlie matched 2 pairs in a row!

### Round Structure
- One round = one complete board
- Can play multiple boards with increasing size

### Scoring
| Action | Points |
|--------|--------|
| Each pair found | 25 pts |
| Hot streak (3+ in a row) | +10 pts per pair |
| Finding the last pair | +25 pts bonus |

### Config (MemoryMatchConfig)
```typescript
{
  gridSize: '4x4' | '6x6' | '4x6',  // default '4x4'
  cardFlipDuration: number,         // how long non-matches stay visible, default 2000ms
  turnTimeLimit: number             // seconds to make selection, default 10
}
```

### Round Info (MemoryMatchRoundInfo)
```typescript
{
  cards: { 
    id: number, 
    symbol: string, 
    isFlipped: boolean, 
    isMatched: boolean 
  }[],
  currentPlayerIndex: number,
  selectedCards: number[],
  matchedPairs: { playerName: string, symbol: string }[],
  phase: 'PLAYING' | 'REVEALING' | 'RESULTS',
  pairsRemaining: number
}
```

### Card Symbol Sets (emojis)
- **FRUITS:** 🍎🍊🍋🍇🍓🍒🥝🍑
- **ANIMALS:** 🐶🐱🐭🐹🐰🦊🐻🐼
- **NATURE:** ⭐🌙☀️⚡🌈💧🔥❄️
- **FOOD:** 🍕🍔🌮🍣🍩🍪🧁🍦
- **SPORTS:** ⚽🏀🏈⚾🎾🏐🏓🎱
- **MUSIC:** 🎸🎹🎺🥁🎻🎷🪘🎤
- **VEHICLES:** 🚗🚕🚌🚑🚒🚁✈️🚀
- **TECH:** 💻📱⌚📷🎮🖨️🔌💡

### Technical Notes
- All players see the same board state in real-time
- Server is source of truth for card positions
- Prevent clicking when it's not your turn
- Animate card flips on frontend

---

## Game 9: Song Lyrics Guess

### Concept
A snippet of song lyrics is shown with key words blanked out. Players race to guess the song title. Reveal more lyrics over time as hints.

### Gameplay Flow
1. Show partial lyrics: "Is this the ____ life? Is this just ____?"
2. Players type their guess
3. Every 5 seconds, reveal another word
4. First to guess correctly wins the round
5. Fewer hints used = more points

### Example Gameplay

**Song 1 of 5 - Genre: CLASSIC ROCK**

**Hint 0 (Initial - Most blanks):**
> "Is this the ____ life? Is this just ____?"  
> "____ in a landslide, no ____ from ____"

🎵 Guess the song title! ⏱️ Next hint in: 5 seconds

Guesses:
- Alice: "Under Pressure" ❌
- Bob: "We Will Rock You" ❌

**Hint 1 (5 seconds - One word revealed):**
> "Is this the **REAL** life? Is this just ____?"

Charlie: "Bohemian Rhapsody" ✓ **CORRECT!**

**Song 1 Results:**
🎵 "Bohemian Rhapsody" by Queen

Full lyrics:
> "Is this the **REAL** life? Is this just **FANTASY**?"  
> "**CAUGHT** in a landslide, no **ESCAPE** from **REALITY**"

🏆 Charlie guessed with 1 hint = **75 points!**

### Progressive Hint Example

**Song 3 of 5 - Genre: 2000s**

| Hint | Lyrics |
|------|--------|
| 0 | "____ out of my ____ and I've been doing just ____" |
| 1 | "**COMING** out of my ____ and I've been doing just ____" |
| 2 | "**COMING** out of my **CAGE** and I've been doing just ____" |
| 3 | "**COMING** out of my **CAGE** and I've been doing just **FINE**" |

Diana guesses "Mr. Brightside" at HINT 2 = **50 points**

### Full Round Example

| Song | Winner | Hints Used | Points |
|------|--------|------------|--------|
| Bohemian Rhapsody | Charlie | 1 | 75 pts |
| Shape of You | Alice | 0 | 100 pts (instant!) |
| Mr. Brightside | Diana | 2 | 50 pts |
| Smells Like Teen Spirit | Bob | 4 | 25 pts |
| Take On Me | Alice | 3 | 50 pts |

**Final Scores:**
| Rank | Player | Points | Notes |
|------|--------|--------|-------|
| 🏆 | Alice | 150 pts | 2 songs, one INSTANT guess! |
| 🥈 | Charlie | 75 pts | |
| 🥉 | Diana | 50 pts | |
| 4 | Bob | 25 pts | |
| 5 | Eve | 0 pts | No correct guesses |

🎤 **MUSIC MASTER:** Alice with an INSTANT guess on "Shape of You"!

### Round Structure
- 5-8 songs per round
- Mix of eras and genres
- Difficulty based on how obscure the song is

### Scoring
| Hints Revealed | Points |
|----------------|--------|
| 0-1 hints | 100 pts |
| 2-3 hints | 75 pts |
| 4-5 hints | 50 pts |
| 6+ hints | 25 pts |
| Incorrect | 0 pts (no penalty) |

### Config (SongLyricsConfig)
```typescript
{
  songsPerRound: number,    // default 5
  hintInterval: number,     // seconds between reveals, default 5
  maxHints: number,         // default 6
  genres: string[]          // optional filter
}
```

### Round Info (SongLyricsRoundInfo)
```typescript
{
  currentSongIndex: number,
  displayedLyrics: string,
  fullLyrics: string,
  hintsRevealed: number,
  songTitle: string,
  artist: string,
  guesses: { 
    playerName: string, 
    guess: string, 
    correct: boolean, 
    hintsAtGuess: number 
  }[],
  winner: string | null,
  phase: 'GUESSING' | 'REVEALING' | 'NEXT_SONG'
}
```

### Song Database (hardcoded samples with hint progression)

**CLASSIC ROCK:**

*"Bohemian Rhapsody" - Queen*
- Hint 0: "Is this the ____ life? Is this just ____?"
- Hint 1: "Is this the REAL life? Is this just ____?"
- Hint 2: "Is this the REAL life? Is this just FANTASY?"
- Hint 3: + "Caught in a ____, no escape from ____"
- Hint 4: + "Caught in a LANDSLIDE, no escape from ____"
- Hint 5: + "Caught in a LANDSLIDE, no escape from REALITY"

*"Don't Stop Believin'" - Journey*
- Hint 0: "Just a ____ town girl, living in a ____ world"
- Hint 1: "Just a SMALL town girl, living in a ____ world"
- Hint 2: "Just a SMALL town girl, living in a LONELY world"
- Hint 3: + "She took the ____ train going ____"
- Hint 4: + "She took the MIDNIGHT train going ANYWHERE"

**POP (2010s-2020s):**

*"Shape of You" - Ed Sheeran*
- Hint 0: "I'm in ____ with the ____ of you"
- Hint 1: "I'm in LOVE with the ____ of you"
- Hint 2: "I'm in LOVE with the SHAPE of you"
- Hint 3: + "We push and ____ like a magnet do"

*"Rolling in the Deep" - Adele*
- Hint 0: "We could have had it ____"
- Hint 1: "We could have had it ALL"
- Hint 2: + "____ in the deep"
- Hint 3: + "ROLLING in the deep"

*(Add 50+ songs for variety)*

### Technical Notes
- Fuzzy matching for song titles (ignore "The", punctuation, etc.)
- Progressive reveal: start with blanks, fill in words over time
- Could add audio snippet playing alongside (future enhancement)

---

## Game 10: Category Countdown

### Concept
A category is given (e.g., "Things that are red"). Players take turns naming items. You have 5 seconds. Repeat something or can't think of anything? You're out!

### Gameplay Flow
1. Category announced: "Name a fruit!"
2. First player types answer within 5 seconds
3. Valid answer = safe, next player's turn
4. Invalid/repeated/timeout = eliminated
5. Last player standing wins

### Example Gameplay

**Round 1 - Category: "NAME A COUNTRY" 🌍**  
**Players: Alice, Bob, Charlie, Diana, Eve**

**Turn Order:** Alice → Bob → Charlie → Diana → Eve → (repeat)

| Turn | Player | Answer | Time | Result |
|------|--------|--------|------|--------|
| 1 | Alice | "France" | 4.2s | ✓ VALID |
| 2 | Bob | "Germany" | 2.1s | ✓ VALID |
| 3 | Charlie | "Japan" | 1.5s | ✓ VALID |
| ... | ... | ... | ... | ... |
| 17 | Eve | "Japan" | 2.1s | ❌ ALREADY USED |

💀 **EVE IS ELIMINATED!** Reason: Repeated answer "Japan"

| Turn | Player | Result |
|------|--------|--------|
| 20 | Bob | ⏱️ TIME'S UP! |

💀 **BOB IS ELIMINATED!** Reason: Timeout (5 seconds)

**Final Showdown: Alice vs Charlie**
| Player | Answers |
|--------|---------|
| Alice | "Switzerland" ✓ |
| Charlie | "Bhutan" ✓ |
| Alice | "Luxembourg" ✓ |
| Charlie | "Liechtenstein" ✓ |
| ... | ... |
| Charlie | ⏱️ TIME'S UP! |

🏆 **ALICE WINS!** Last player standing!

**Round Results:**
| Rank | Player | Points | Answers Given |
|------|--------|--------|---------------|
| 🏆 1st | Alice | 100 pts | 8 answers |
| 🥈 2nd | Charlie | 75 pts | 7 answers |
| 🥉 3rd | Diana | 50 pts | 4 answers |
| 4th | Bob | 25 pts | 3 answers (timeout) |
| 5th | Eve | 25 pts | 3 answers (repeated) |

📊 Total unique countries named: **31**

### Round Structure
- Elimination style
- Categories get harder each round
- Some categories have limited valid answers (pressure!)

### Scoring
| Placement | Points |
|-----------|--------|
| Last standing | 100 pts |
| 2nd | 75 pts |
| 3rd | 50 pts |
| Others | 25 pts |

### Config (CategoryCountdownConfig)
```typescript
{
  turnDuration: number,              // seconds, default 5
  categories: string[],              // pulled from list
  difficultyProgression: boolean     // categories get harder
}
```

### Round Info (CategoryCountdownRoundInfo)
```typescript
{
  category: string,
  answers: { playerName: string, answer: string, valid: boolean }[],
  currentPlayerIndex: number,
  activePlayers: string[],
  eliminatedPlayers: { 
    name: string, 
    reason: 'timeout' | 'repeated' | 'invalid' 
  }[],
  phase: 'PLAYING' | 'VALIDATING' | 'RESULTS'
}
```

### Category Ideas by Difficulty

**EASY (many valid answers):**
Fruits, Colors, Animals, Countries, Sports, Vegetables, Car brands, Ice cream flavors, Pizza toppings, Breakfast foods

**MEDIUM (requires more thought):**
Things in a kitchen, Movie genres, Musical instruments, Things you find at a beach, Types of candy, Things that are round, Things you plug in, Things with wheels, Things that fly, Words starting with "S"

**HARD (limited answers or obscure):**
U.S. State capitals, Elements on the periodic table, Shakespeare plays, Greek gods, Olympic sports, Nobel Prize categories, Planets and moons, Programming languages, Board games, Types of cheese

**IMPOSSIBLE/FUN (subjective, hilarious):**
Things you shouldn't say at a job interview, Reasons to be late to work, Bad superhero powers, Excuses for not doing homework, Things to yell in a library, Bad first date topics, Things that smell weird, Useless superpowers, Things you hide from your parents, Awkward compliments

### Technical Notes
- Simple string matching (case insensitive)
- Track all used answers to prevent repeats
- Some categories could have a validation list, others are "trust based"
- Could add majority vote validation for subjective categories

---

## Game 11: Number Guess ✅ IMPLEMENTED

### Concept
Players try to guess a randomly generated target number within a specified range. The player whose guess is closest to the target wins the round!

### Gameplay Flow
1. Server generates a random target number within the configured range (default 1-100)
2. Round timer starts (default 30 seconds)
3. All players submit their guesses simultaneously
4. When timer ends, the target number is revealed
5. Player with the closest guess wins the round
6. Points awarded based on how close each player's guess was

### Example Gameplay

**Round 1 - Range: 1-100**

**Target Number: 67** (hidden until reveal)

**Players Guessing:**
| Player | Guess | Status |
|--------|-------|--------|
| Alice | 72 | ✓ Submitted |
| Bob | 45 | ✓ Submitted |
| Charlie | 68 | ✓ Submitted |
| Diana | 50 | ✓ Submitted |

**Results Reveal:**

Target Number: **67**

| Rank | Player | Guess | Difference | Points |
|------|--------|-------|------------|--------|
| 🏆 1st | Charlie | 68 | 1 | **100 pts** (exact match bonus!) |
| 🥈 2nd | Alice | 72 | 5 | **47 pts** |
| 🥉 3rd | Diana | 50 | 17 | **41 pts** |
| 4th | Bob | 45 | 22 | **39 pts** |

### Round Structure
- Each round has a new random target number
- All players guess simultaneously
- Default 30-second timer per round
- Multiple rounds with cumulative scoring

### Scoring
| Condition | Points |
|-----------|--------|
| Exact match (diff = 0) | 100 pts |
| Close guess | 50 - (difference / 2) pts |
| Minimum | 0 pts |

### Config (NumberGuessConfig)
```typescript
{
  type: 'number-guess',
  roundDuration: number,       // seconds, default 30
  minRange: number,            // minimum value, default 1
  maxRange: number             // maximum value, default 100
}
```

### Round Info (NumberGuessRoundInfo)
```typescript
{
  gameType: 'number-guess',
  roundNumber: number,
  winner: string | null,
  revealed: boolean,
  minRange: number,
  maxRange: number,
  playerGuesses: Record<string, number>,
  targetNumber: number | null  // null until revealed
}
```

### Technical Notes
- Target number generated server-side using `Math.random()`
- Guesses submitted via WebSocket: `/app/guess`
- Target hidden until round ends (revealed = true)
- Round ends when timer expires or all players submit

---

## Game 12: Word Scramble ✅ IMPLEMENTED

### Concept
A word is scrambled and displayed to all players. Race to unscramble and guess the original word! The first player to guess correctly wins, but other correct guesses still earn points.

### Gameplay Flow
1. Server selects a random word and scrambles it
2. Scrambled word is displayed to all players
3. Players type their guesses
4. First correct guess ends the round early (wins!)
5. Other correct guesses still earn partial points
6. Round ends when timer expires if no correct guess

### Example Gameplay

**Round 1 - Word Length: 7 letters**

**Scrambled Word: "ELPMOCE"**

**Players Guessing:**
| Player | Guess | Time | Status |
|--------|-------|------|--------|
| Alice | "WELCOME" | 3.2s | ❌ Incorrect |
| Bob | "COMPLETE" | 4.8s | ❌ Incorrect |
| Charlie | "COMPILE" | 5.1s | ❌ Incorrect |
| Diana | "COMPETE" | 6.3s | ❌ Incorrect |
| Alice | "COMPILE" | 7.5s | ❌ Incorrect |
| Bob | "POMECLE" | 8.1s | ❌ Incorrect |
| Diana | "EMPEL" | 9.2s | ❌ Incorrect |
| Charlie | "COMPILE" | 11.4s | ✓ **CORRECT!** |

**Round Ends Early!**

**Results:**

The word was: **COMPILE**

| Rank | Player | Points | Notes |
|------|--------|--------|-------|
| 🏆 1st | Charlie | **100 pts** | First correct answer! |
| Others | Alice, Bob, Diana | **0 pts** | No correct guess |

### Multiple Correct Example

**Round 2 - Word: "ORCGNIA" → ORGANIC**

| Rank | Player | Time | Points |
|------|--------|------|--------|
| 🏆 1st | Alice | 4.2s | **100 pts** (fastest) |
| 2nd | Diana | 5.8s | **50 pts** (correct but not first) |
| 3rd | Bob | 7.1s | **50 pts** (correct but not first) |
| - | Charlie | - | **0 pts** (no correct guess) |

### Round Structure
- Each round has a new scrambled word
- Word length is configurable (default 7 letters)
- Round ends immediately when first correct guess is made
- Default 30-second timer per round

### Scoring
| Condition | Points |
|-----------|--------|
| First correct answer | 100 pts |
| Correct answer (not first) | 50 pts |
| Incorrect/No answer | 0 pts |

### Config (WordScrambleConfig)
```typescript
{
  type: 'word-scramble',
  roundDuration: number,       // seconds, default 30
  wordLength: number           // target word length, default 7
}
```

### Round Info (WordScrambleRoundInfo)
```typescript
{
  gameType: 'word-scramble',
  roundNumber: number,
  winner: string | null,
  revealed: boolean,
  scrambledWord: string,
  originalWord: string | null,  // null until revealed
  playerWordGuesses: Record<string, string>
}
```

### Word Sources
- Words are pulled from a server-side word list
- Filtered by configured word length
- Scrambling algorithm ensures word is actually shuffled

### Technical Notes
- Guesses submitted via WebSocket: `/app/wordGuess`
- Case-insensitive matching
- Original word hidden until round ends or correct guess
- Tracks answer times for determining winner (fastest correct guess)

---

## Game 13: Pictionary ✅ IMPLEMENTED

### Concept
Classic drawing and guessing game! One player draws a secret word while others try to guess it. Each player takes a turn drawing in a round, and points are awarded to both successful drawers and guessers.

### Gameplay Flow
1. Round starts with first player as "drawer"
2. Drawer sees the secret word to draw
3. Other players see a hint (underscores showing word length)
4. Drawer draws on canvas, strokes broadcast in real-time
5. Guessers type guesses - first correct guess wins the turn!
6. Both drawer and guesser earn points for successful guess
7. Next player becomes drawer
8. Round ends when all players have drawn once

### Example Gameplay

**Round 1 - Turn 1 of 4 (4 players)**

**Drawer: Alice**  
**Secret Word: "ELEPHANT" 🐘** (only Alice sees this)  
**Hint for guessers: "_ _ _ _ _ _ _ _"** (8 letters)

**Live Drawing:**
```
Alice is drawing...

┌────────────────────────────────┐
│                                │
│      🖌️ [Canvas with           │
│         elephant drawing]      │
│                                │
└────────────────────────────────┘
```

**Guesses (real-time):**
| Player | Guess | Result |
|--------|-------|--------|
| Bob | "animal" | ❌ |
| Charlie | "dog" | ❌ |
| Diana | "trunk" | ❌ |
| Bob | "elephant" | ✅ **CORRECT!** |

**Turn 1 Results:**
| Player | Points | Reason |
|--------|--------|--------|
| Bob (Guesser) | +100 pts | Guessed correctly! |
| Alice (Drawer) | +50 pts | Word was guessed |

**Turn 2 of 4 - Bob is now the drawer...**

### Full Round Summary

**Round 1 Complete - All 4 turns finished:**

| Turn | Drawer | Word | Guesser | Drawer Pts | Guesser Pts |
|------|--------|------|---------|------------|-------------|
| 1 | Alice | ELEPHANT | Bob | 50 | 100 |
| 2 | Bob | PIZZA | Diana | 50 | 100 |
| 3 | Charlie | RAINBOW | Alice | 50 | 100 |
| 4 | Diana | GUITAR | - | 0 | 0 |

**Round 1 Final Scores:**
| Rank | Player | Total Points |
|------|--------|--------------|
| 🏆 | Alice | 150 pts (50 drawer + 100 guesser) |
| 🥈 | Bob | 150 pts (50 drawer + 100 guesser) |
| 🥉 | Diana | 150 pts (50 drawer + 100 guesser) |
| 4th | Charlie | 50 pts (50 drawer only) |

### Turn Structure
- Each turn has one drawer
- All other players are guessers
- Turn ends when someone guesses correctly OR timer expires
- Each player draws once per round

### Scoring
| Action | Points |
|--------|--------|
| Guesser (correct guess) | 100 pts |
| Drawer (word was guessed) | 50 pts |
| No correct guess | 0 pts (both) |

### Config (PictionaryConfig)
```typescript
{
  type: 'pictionary',
  roundDuration: number,       // seconds per turn, default 30
  currentTurn: number,         // current turn number (1-indexed)
  totalTurns: number           // total turns = number of players
}
```

### Round Info (PictionaryRoundInfo)
```typescript
{
  gameType: 'pictionary',
  roundNumber: number,
  winner: string | null,       // correct guesser
  revealed: boolean,
  drawerName: string,
  wordToDraw: string | null,   // shown to drawer, hidden from guessers
  wordHint: string,            // "_ _ _ _ _" format
  drawingData: DrawingStroke[],
  playerPictionaryGuesses: Record<string, string[]>,
  correctGuesser: string | null,
  currentTurnNumber: number,
  totalTurns: number,
  turnResults: TurnResult[]
}
```

### Drawing Tools
- **Colors:** Multiple color palette including black, red, blue, green, yellow, orange, purple, pink, brown
- **Brush Size:** Adjustable stroke width
- **Clear:** Erase entire canvas
- **Undo:** Remove last stroke

### Word List Categories
- Animals (elephant, giraffe, penguin...)
- Food (pizza, hamburger, sushi...)
- Objects (guitar, umbrella, bicycle...)
- Nature (rainbow, mountain, ocean...)
- And many more!

### Technical Notes
- Drawing strokes broadcast via WebSocket: `/app/draw`
- Guesses submitted via WebSocket: `/app/pictionaryGuess`
- Case-insensitive guess matching
- Real-time canvas sync for all players
- Drawer cannot guess their own word
- Turn results stored for round summary display

---

## Implementation Priority

### Phase 1: Quick Wins (Similar to existing games)
1. **Trivia Blitz** (like Number Guess but with questions)
2. **Speed Typing Race** (simple, engaging)
3. **Category Countdown** (elimination style, simple logic)

### Phase 2: Moderate Complexity
4. **Reaction Showdown** (timing-based, new mechanic)
5. **Word Association Chain** (turn-based elimination)
6. **Song Lyrics Guess** (progressive hints)

### Phase 3: More Complex
7. **Emoji Story Chain** (creative, voting)
8. **Imposter Word** (social deduction)
9. **Quick Draw Battle** (extends Pictionary)
10. **Memory Match Race** (shared game board)

---

## Architecture Notes

For each new game, create:

### Backend
1. `backend/src/main/java/com/example/funfridaygame/model/config/{Game}Config.java`
2. `backend/src/main/java/com/example/funfridaygame/model/round/{Game}Round.java`
3. `backend/src/main/java/com/example/funfridaygame/model/roundinfo/{Game}RoundInfo.java`
4. `backend/src/main/java/com/example/funfridaygame/service/game/{Game}Strategy.java`
5. Add to `BaseGameConfig` `@JsonSubTypes`
6. Add to `BaseRoundInfo` `@JsonSubTypes` (if needed)
7. Register `GameType` in `GameTypeRegistry`

### Frontend
1. `frontend/src/app/models/game.model.ts` - Add config and round info interfaces
2. `frontend/src/app/utils/game-config.utils.ts` - Add type guards
3. `frontend/src/app/components/games/{game}/` - Create game component
4. Add route in `app.routes.ts`
5. Add to game type list
