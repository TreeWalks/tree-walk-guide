package dev.csaba.armap.treewalk.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import dev.csaba.armap.treewalk.R
import dev.csaba.armap.treewalk.data.InfoRow


open class InfoListAdapter(context: Context, resource: Int, objects: ArrayList<InfoRow>) :
        ArrayAdapter<InfoRow>(context, resource, objects) {

    private var resource: Int
    private var infoList: ArrayList<InfoRow>
    private var layoutInflater: LayoutInflater

    init {
        this.resource = resource
        this.infoList = objects
        this.layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    @SuppressLint("ViewHolder")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView: View =
            layoutInflater.inflate(R.layout.info_row, parent, false)
        // val pic: ImageView = rowView.findViewById(R.id.rowImage)
        val infoRow: InfoRow = infoList[position]
        val label: TextView = rowView.findViewById(R.id.listRowLabel)
        label.text = infoRow.label
        val value: TextView = rowView.findViewById(R.id.listRowValue)
        value.text = infoRow.value
        // val bitmap: Bitmap = user.getUserProfileBitmap()
        // pic.setImageDrawable(bitmap)
        return rowView
    }
}