package nl.mpcjanssen.simpletask.fileswitch

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import nl.mpcjanssen.simpletask.R

class FavoriteFileListAdapter(
    context: Context,
    private val onSelect: (FavoriteTodoFile) -> Unit,
    private val onRename: (FavoriteTodoFile) -> Unit,
    private val onRemove: (FavoriteTodoFile) -> Unit
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val rows = mutableListOf<FavoriteFileSwitcherRow>()

    override fun getCount(): Int = rows.size

    override fun getItem(position: Int): FavoriteFileSwitcherRow = rows[position]

    override fun getItemId(position: Int): Long = getItem(position).favorite.path.hashCode().toLong()

    fun submitRows(updatedRows: List<FavoriteFileSwitcherRow>) {
        rows.clear()
        rows.addAll(updatedRows)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.favorite_file_switcher_row, parent, false)
        val row = getItem(position)

        val title = view.findViewById<TextView>(R.id.favorite_file_name)
        val subtitle = view.findViewById<TextView>(R.id.favorite_file_path)
        val badge = view.findViewById<TextView>(R.id.favorite_file_active_badge)
        val renameButton = view.findViewById<ImageButton>(R.id.favorite_file_rename)
        val removeButton = view.findViewById<ImageButton>(R.id.favorite_file_remove)

        title.text = row.title
        subtitle.text = row.subtitle
        subtitle.visibility = if (row.subtitle.isBlank()) View.GONE else View.VISIBLE
        badge.visibility = if (row.isActive) View.VISIBLE else View.GONE

        view.setOnClickListener { onSelect(row.favorite) }
        renameButton.setOnClickListener { onRename(row.favorite) }
        renameButton.contentDescription = view.context.getString(R.string.favorite_file_label_edit)
        removeButton.setOnClickListener { onRemove(row.favorite) }
        removeButton.contentDescription = view.context.getString(R.string.favorite_file_remove)

        return view
    }
}
