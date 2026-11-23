-- Filtre pandoc utilisé par generer.sh : les tableaux GFM n'ont pas de largeurs de colonnes,
-- LaTeX les rendrait sur une seule ligne (débordement). On donne à chaque colonne une largeur
-- proportionnelle à son contenu le plus long, pour que le texte passe à la ligne.
local function len(s) return utf8.len(s) or #s end

function Table(tbl)
  local n, max, word = #tbl.colspecs, {}, {}
  for i = 1, n do max[i], word[i] = 4, 0 end
  local function scan(rows)
    for _, row in ipairs(rows) do
      for i, cell in ipairs(row.cells) do
        local s = pandoc.utils.stringify(cell.contents)
        max[i] = math.max(max[i], len(s))
        for w in s:gmatch('%S+') do word[i] = math.max(word[i], len(w)) end
      end
    end
  end
  scan(tbl.head.rows)
  for _, body in ipairs(tbl.bodies) do scan(body.body) end
  local total = 0
  -- plafond 70 pour qu'une colonne bavarde n'écrase pas les autres, plancher = mot le plus long
  for i = 1, n do max[i] = math.max(math.min(max[i], 70), word[i] * 2.5); total = total + max[i] end
  if total < 70 then return nil end -- tableau court : largeur naturelle
  for i = 1, n do tbl.colspecs[i][2] = max[i] / total end
  return tbl
end

-- Code en ligne long (noms de tests en camelCase, chemins) : points de coupure après . _ / -
-- et avant une majuscule, sinon il déborde des colonnes de tableau.
function Code(c)
  if utf8.len(c.text) < 20 then return nil end
  local out, seg = {}, ''
  for _, cp in utf8.codes(c.text) do
    local ch = utf8.char(cp)
    if ch:match('%u') and seg ~= '' and not seg:match('%u$') then
      out[#out + 1] = pandoc.Code(seg); out[#out + 1] = pandoc.RawInline('latex', '\\allowbreak{}'); seg = ''
    end
    seg = seg .. ch
    if ch:match('[%._/%-]') then
      out[#out + 1] = pandoc.Code(seg); out[#out + 1] = pandoc.RawInline('latex', '\\allowbreak{}'); seg = ''
    end
  end
  if seg ~= '' then out[#out + 1] = pandoc.Code(seg) end
  return out
end

-- Le bandeau en citation sous le titre (module, groupe) devient le sous-titre de la page de garde
function Pandoc(doc)
  if doc.blocks[1] and doc.blocks[1].t == 'BlockQuote' then
    doc.meta.subtitle = pandoc.MetaBlocks(doc.blocks[1].content)
    doc.blocks:remove(1)
  end
  return doc
end

-- Image seule dans son paragraphe avec un texte alternatif : figure numérotée avec légende
function Para(p)
  local img = p.content[1]
  if #p.content == 1 and img.t == 'Image' and #img.caption > 0 then
    return pandoc.Figure(pandoc.Plain{pandoc.Image({}, img.src)}, {pandoc.Plain(img.caption)})
  end
end

-- Blocs de code sans langage : Verbatim avec retour à la ligne automatique (fvextra)
function CodeBlock(cb)
  if #cb.classes == 0 then
    return pandoc.RawBlock('latex',
      '\\begin{Verbatim}[breaklines,fontsize=\\small]\n' .. cb.text .. '\n\\end{Verbatim}')
  end
end
