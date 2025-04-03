package dev.lucy.momentsintime

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class InstructionPagerAdapter(private val instructionPages: List<String>) : 
    RecyclerView.Adapter<InstructionPagerAdapter.InstructionViewHolder>() {
    
    class InstructionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.instructionTextView)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstructionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.instruction_page_item, parent, false)
        return InstructionViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: InstructionViewHolder, position: Int) {
        holder.textView.text = instructionPages[position]
    }
    
    override fun getItemCount(): Int = instructionPages.size
}
