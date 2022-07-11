
<template>
<div>
  <Bar
    :chart-options="chartOptions"
    :chart-data="chartData"
    :chart-id="chartId"
    :dataset-id-key="datasetIdKey"
    :plugins="plugins"
    :css-classes="cssClasses"
    :styles="styles"
    :width="width"
    :height="height"
  />
</div>
</template>

<script>
import { Bar } from "vue-chartjs/legacy";

import {
  Chart as ChartJS,
  Title,
  Tooltip,
  Legend,
  BarElement,
  CategoryScale,
  LinearScale,
} from "chart.js";

ChartJS.register(
  Title,
  Tooltip,
  Legend,
  BarElement,
  CategoryScale,
  LinearScale
);

export default {
  name: "BarChart",
  components: {
    Bar,
  },
  props: {
    chartId: {
      type: String,
      default: "bar-chart",
    },
    datasetIdKey: {
      type: String,
      default: "label",
    },
    width: {
      type: Number,
      default: 400,
    },
    height: {
      type: Number,
      default: 400,
    },
    cssClasses: {
      default: "",
      type: String,
    },
    styles: {
      type: Object,
      default: () => {},
    },
    plugins: {
      type: Array,
      default: () => [],
    },
    color:{
      type:String,
      default:"#f87979"
    },
    labels:{
      type:Array
    },
    data:{
      type:Array
    },
    title:{
      type:String,
      default:"title "
    },
    datasets:{
      type:Array
    }
  },
  data() {
    return {
      chartOptions: {
        responsive: true,
        maintainAspectRatio: false,
      },
    };
  },
  computed: {
    chartData: function () {
      return {
        labels: this.labels ,
        datasets: this.datasets
        // [
        //   {
        //     label:this.title,
        //     backgroundColor: this.color,
        //     data: this.data,
        //   },
        //   {
        //     label:this.title,
        //     backgroundColor: this.color,
        //     data: this.data,
        //   },
        //   {
        //     label:this.title,
        //     backgroundColor: this.color,
        //     data: this.data,
        //   },
        // ],
      };
    },
  },
};
</script>